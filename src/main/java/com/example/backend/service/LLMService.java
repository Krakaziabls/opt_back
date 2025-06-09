package com.example.backend.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.example.backend.config.LLMConfig;
import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
@RequiredArgsConstructor
@Slf4j
public class LLMService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 1000;

    private final LLMConfig llmConfig;
    private final GigaChatAuthService gigaChatAuthService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;
    private final RestTemplate localRestTemplate;

    public Mono<String> optimizeSqlQuery(String query, String llmProvider) {
        log.debug("Optimizing SQL query with provider: {}", llmProvider);
        if ("Local".equals(llmProvider)) {
            return optimizeWithLocalLLM(query);
        } else {
            return optimizeWithCloudLLM(query);
        }
    }

    private Mono<String> optimizeWithLocalLLM(String query) {
        log.debug("Attempting to optimize with local LLM. Local LLM enabled: {}", llmConfig.isLocalEnabled());
        if (!llmConfig.isLocalEnabled()) {
            log.error("Local LLM is not enabled");
            return Mono.error(new ApiException("Local LLM is not enabled", HttpStatus.SERVICE_UNAVAILABLE));
        }

        try {
            Map<String, Object> requestBody = prepareRequestBody(query);
            log.debug("Prepared request body for local LLM: {}", requestBody);
            
            return Mono.fromCallable(() -> {
                try {
                    log.debug("Sending request to local LLM at URL: {}", llmConfig.getLocalApiUrl());
                    String response = localRestTemplate.postForObject(
                        llmConfig.getLocalApiUrl() + "/v1/chat/completions",
                        requestBody,
                        String.class
                    );
                    
                    if (response == null) {
                        log.error("Empty response from local LLM");
                        throw new ApiException("Empty response from local LLM", HttpStatus.INTERNAL_SERVER_ERROR);
                    }

                    log.debug("Received response from local LLM: {}", response);
                    JsonNode rootNode = objectMapper.readTree(response);
                    JsonNode choicesNode = rootNode.path("choices");
                    
                    if (choicesNode.isArray() && choicesNode.size() > 0) {
                        JsonNode firstChoice = choicesNode.get(0);
                        JsonNode messageNode = firstChoice.path("message");
                        String content = messageNode.path("content").asText();
                        return extractSqlFromResponse(content);
                    }
                    
                    log.error("Invalid response format from local LLM: {}", response);
                    throw new ApiException("Invalid response format from local LLM", HttpStatus.INTERNAL_SERVER_ERROR);
                } catch (Exception e) {
                    log.error("Error calling local LLM: {}", e.getMessage(), e);
                    throw new ApiException("Error calling local LLM: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
                }
            }).retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(RETRY_DELAY_MS))
                .filter(e -> e instanceof ApiException && 
                    ((ApiException) e).getStatus() == HttpStatus.SERVICE_UNAVAILABLE));
        } catch (Exception e) {
            log.error("Failed to prepare request for local LLM: {}", e.getMessage(), e);
            return Mono.error(new ApiException("Failed to prepare request for local LLM: " + e.getMessage(), 
                HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private Mono<String> optimizeWithCloudLLM(String query) {
        if (!StringUtils.hasText(query)) {
            return Mono.error(new ApiException("SQL query cannot be empty", HttpStatus.BAD_REQUEST));
        }

        return Mono.defer(() -> {
            validateConfiguration();
            Map<String, Object> requestBody = prepareRequestBody(query);

            log.debug("Making request to LLM API: URL={}, Body={}",
                    llmConfig.getApiUrl() + "/chat/completions",
                    requestBody);

            return gigaChatAuthService.getWebClient()
                    .flatMap(webClient -> webClient.post()
                            .uri("/chat/completions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(requestBody)
                            .retrieve()
                            .onStatus(status -> status.is4xxClientError(),
                                    clientResponse -> handleClientError(clientResponse))
                            .onStatus(status -> status.is5xxServerError(),
                                    clientResponse -> handleServerError(clientResponse))
                            .bodyToMono(String.class))
                    .flatMap(this::parseResponse)
                    .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, java.time.Duration.ofMillis(RETRY_DELAY_MS))
                            .doBeforeRetry(signal -> log.warn("Retrying LLM API call, attempt: {}", signal.totalRetries() + 1))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> 
                                new ApiException("Failed to optimize SQL query after " + MAX_RETRY_ATTEMPTS + " attempts",
                                        HttpStatus.SERVICE_UNAVAILABLE)))
                    .onErrorMap(e -> {
                        if (e instanceof ApiException) {
                            return e;
                        }
                        log.error("Error calling LLM API: {}", e.getMessage(), e);
                        return new ApiException("Error optimizing SQL query: " + e.getMessage(),
                                HttpStatus.INTERNAL_SERVER_ERROR);
                    });
        });
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(llmConfig.getModel())) {
            throw new IllegalStateException("LLM model is not configured");
        }
        if (!StringUtils.hasText(llmConfig.getSystemPrompt())) {
            throw new IllegalStateException("LLM system prompt is not configured");
        }
        if (llmConfig.getTemperature() < 0 || llmConfig.getTemperature() > 1) {
            throw new IllegalStateException("LLM temperature must be between 0 and 1");
        }
        if (llmConfig.getMaxTokens() <= 0) {
            throw new IllegalStateException("LLM max tokens must be positive");
        }
    }

    private Map<String, Object> prepareRequestBody(String query) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmConfig.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", llmConfig.getSystemPrompt()));
        messages.add(Map.of("role", "user", "content", "Optimize this SQL query for PostgreSQL: " + query));

        requestBody.put("messages", messages);
        requestBody.put("temperature", llmConfig.getTemperature());
        requestBody.put("max_tokens", llmConfig.getMaxTokens());

        return requestBody;
    }

    private Mono<? extends Throwable> handleClientError(org.springframework.web.reactive.function.client.ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
                .flatMap(errorBody -> {
                    log.error("LLM API client error: status={}, body={}",
                            clientResponse.statusCode(), errorBody);
                    return Mono.error(new ApiException(
                            "Invalid request to LLM API: " + errorBody,
                            HttpStatus.BAD_REQUEST));
                });
    }

    private Mono<? extends Throwable> handleServerError(org.springframework.web.reactive.function.client.ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
                .flatMap(errorBody -> {
                    log.error("LLM API server error: status={}, body={}",
                            clientResponse.statusCode(), errorBody);
                    return Mono.error(new ApiException(
                            "LLM API server error: " + errorBody,
                            HttpStatus.SERVICE_UNAVAILABLE));
                });
    }

    private Mono<String> parseResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return Mono.error(new ApiException("Empty response from LLM", HttpStatus.INTERNAL_SERVER_ERROR));
        }

        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode choicesNode = rootNode.path("choices");

            if (!choicesNode.isArray() || choicesNode.size() == 0) {
                log.error("Invalid response format: no choices array or empty choices");
                return Mono.error(new ApiException("Invalid response format from LLM: no choices available",
                        HttpStatus.INTERNAL_SERVER_ERROR));
            }

            JsonNode firstChoice = choicesNode.get(0);
            JsonNode messageNode = firstChoice.path("message");
            
            if (!messageNode.has("content")) {
                log.error("Invalid response format: no content in message");
                return Mono.error(new ApiException("Invalid response format from LLM: no content in message",
                        HttpStatus.INTERNAL_SERVER_ERROR));
            }

            String content = messageNode.path("content").asText();
            String extractedSql = extractSqlFromResponse(content);
            
            if (!StringUtils.hasText(extractedSql)) {
                log.error("No SQL query found in LLM response");
                return Mono.error(new ApiException("No SQL query found in LLM response",
                        HttpStatus.INTERNAL_SERVER_ERROR));
            }

            return Mono.just(extractedSql);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage(), e);
            return Mono.error(new ApiException("Failed to parse LLM response: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private String extractSqlFromResponse(String response) {
        // Extract SQL code between ```sql and ``` markers
        if (response.contains("```sql")) {
            int start = response.indexOf("```sql") + 6;
            int end = response.indexOf("```", start);
            if (end > start) {
                String sql = response.substring(start, end).trim();
                String text = response.substring(0, response.indexOf("```sql")).trim();
                return text + "\n\n" + sql;
            }
        }
        // Fallback: return the whole response if no code block is found
        return response.trim();
    }
}
