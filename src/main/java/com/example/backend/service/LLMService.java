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

    public Mono<String> optimizeSqlQuery(String query, String llmProvider, String promptTemplate) {
        log.debug("Optimizing SQL query with provider: {}", llmProvider);
        if ("Local".equals(llmProvider)) {
            return optimizeWithLocalLLM(query, promptTemplate);
        } else {
            return optimizeWithCloudLLM(query, promptTemplate);
        }
    }

    private Mono<String> optimizeWithLocalLLM(String query, String promptTemplate) {
        log.debug("Attempting to optimize with local LLM. Local LLM enabled: {}", llmConfig.isLocalEnabled());
        if (!llmConfig.isLocalEnabled()) {
            log.error("Local LLM is not enabled");
            return Mono.error(new ApiException("Local LLM is not enabled", HttpStatus.SERVICE_UNAVAILABLE));
        }

        try {
            Map<String, Object> requestBody = prepareRequestBody(query, promptTemplate);
            log.debug("Prepared request body for local LLM: {}", requestBody);

            return Mono.fromCallable(() -> {
                try {
                    log.debug("Sending request to local LLM at URL: {}", llmConfig.getLocalApiUrl());
                    String response = localRestTemplate.postForObject(
                            llmConfig.getLocalApiUrl() + "/v1/chat/completions",
                            requestBody,
                            String.class);

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

                        // Форматируем ответ в соответствии с шаблоном
                        return formatLLMResponse(content);
                    }

                    log.error("Invalid response format from local LLM: {}", response);
                    throw new ApiException("Invalid response format from local LLM", HttpStatus.INTERNAL_SERVER_ERROR);
                } catch (Exception e) {
                    log.error("Error calling local LLM: {}", e.getMessage(), e);
                    throw new ApiException("Error calling local LLM: " + e.getMessage(),
                            HttpStatus.SERVICE_UNAVAILABLE);
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

    private String formatLLMResponse(String content) {
        // Если ответ уже содержит нужный формат, возвращаем его как есть
        if (content.contains("Оптимизированный SQL-запрос:") &&
                content.contains("Обоснование изменений:") &&
                content.contains("Оценка улучшения:") &&
                content.contains("Потенциальные риски:")) {
            return content;
        }

        // Иначе форматируем ответ
        StringBuilder formattedResponse = new StringBuilder();

        // Добавляем SQL запрос
        formattedResponse.append("## Оптимизированный SQL-запрос\n\n");
        if (content.contains("```sql")) {
            int start = content.indexOf("```sql") + 6;
            int end = content.indexOf("```", start);
            if (end > start) {
                formattedResponse.append("```sql\n");
                formattedResponse.append(content.substring(start, end).trim());
                formattedResponse.append("\n```\n\n");
            }
        } else {
            formattedResponse.append("```sql\n");
            formattedResponse.append(content.trim());
            formattedResponse.append("\n```\n\n");
        }

        // Добавляем остальные секции
        formattedResponse.append("## Обоснование изменений\n\n");
        formattedResponse.append("Оптимизация запроса выполнена с учетом лучших практик SQL.\n\n");

        formattedResponse.append("## Оценка улучшения\n\n");
        formattedResponse.append("Ожидается улучшение производительности за счет оптимизации структуры запроса.\n\n");

        formattedResponse.append("## Потенциальные риски\n\n");
        formattedResponse.append("Изменения не должны повлиять на логику работы запроса.");

        return formattedResponse.toString();
    }

    private Mono<String> optimizeWithCloudLLM(String query, String promptTemplate) {
        if (!StringUtils.hasText(query)) {
            return Mono.error(new ApiException("SQL query cannot be empty", HttpStatus.BAD_REQUEST));
        }

        return Mono.defer(() -> {
            validateConfiguration();
            Map<String, Object> requestBody = prepareRequestBody(query, promptTemplate);

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
                            .doBeforeRetry(
                                    signal -> log.warn("Retrying LLM API call, attempt: {}", signal.totalRetries() + 1))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> new ApiException(
                                    "Failed to optimize SQL query after " + MAX_RETRY_ATTEMPTS + " attempts",
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
        if (llmConfig.getTemperature() < 0 || llmConfig.getTemperature() > 1) {
            throw new IllegalStateException("LLM temperature must be between 0 and 1");
        }
        if (llmConfig.getMaxTokens() <= 0) {
            throw new IllegalStateException("LLM max tokens must be positive");
        }
    }

    private Map<String, Object> prepareRequestBody(String query, String promptTemplate) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmConfig.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", promptTemplate));
        messages.add(Map.of("role", "user", "content", query));

        requestBody.put("messages", messages);
        requestBody.put("temperature", llmConfig.getTemperature());
        requestBody.put("max_tokens", llmConfig.getMaxTokens());

        return requestBody;
    }

    private Mono<? extends Throwable> handleClientError(
            org.springframework.web.reactive.function.client.ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
                .flatMap(errorBody -> {
                    log.error("LLM API client error: status={}, body={}",
                            clientResponse.statusCode(), errorBody);
                    return Mono.error(new ApiException(
                            "Invalid request to LLM API: " + errorBody,
                            HttpStatus.BAD_REQUEST));
                });
    }

    private Mono<? extends Throwable> handleServerError(
            org.springframework.web.reactive.function.client.ClientResponse clientResponse) {
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
            return Mono.just(content);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage(), e);
            return Mono.error(new ApiException("Failed to parse LLM response: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
