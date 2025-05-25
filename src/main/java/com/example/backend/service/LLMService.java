package com.example.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.example.backend.config.LLMConfig;
import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class LLMService {

    private final LLMConfig llmConfig;
    private final GigaChatAuthService gigaChatAuthService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<String> optimizeSqlQuery(String sqlQuery) {
        return Mono.defer(() -> {
            // Prepare request body for GigaChat API
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", llmConfig.getModel());

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", llmConfig.getSystemPrompt()));
            messages.add(Map.of("role", "user", "content", "Optimize this SQL query for PostgreSQL: " + sqlQuery));

            requestBody.put("messages", messages);
            requestBody.put("temperature", llmConfig.getTemperature());
            requestBody.put("max_tokens", llmConfig.getMaxTokens());

            log.debug("Making request to LLM API: URL={}, Body={}",
                    llmConfig.getApiUrl() + "/chat/completions",
                    requestBody);

            return gigaChatAuthService.getWebClient()
                    .flatMap(webClient -> webClient.post()
                            .uri("/chat/completions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(requestBody)
                            .retrieve()
                            .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                    clientResponse -> clientResponse.bodyToMono(String.class)
                                            .flatMap(errorBody -> {
                                                log.error("LLM API error: status={}, body={}",
                                                        clientResponse.statusCode(), errorBody);
                                                return Mono.error(new RuntimeException(
                                                        "Failed to call LLM API: " + errorBody));
                                            }))
                            .bodyToMono(String.class))
                    .flatMap(response -> {
                        log.debug("Received response from LLM API: Body={}", response);
                        return parseResponse(response);
                    })
                    .onErrorMap(e -> {
                        log.error("Error calling LLM API: {}", e.getMessage(), e);
                        return new ApiException("Error optimizing SQL query: " + e.getMessage(),
                                HttpStatus.INTERNAL_SERVER_ERROR);
                    });
        });
    }

    private Mono<String> parseResponse(String response) {
        if (response == null) {
            return Mono.error(new ApiException("Empty response from LLM", HttpStatus.INTERNAL_SERVER_ERROR));
        }

        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode choicesNode = rootNode.path("choices");

            if (choicesNode.isArray() && choicesNode.size() > 0) {
                JsonNode firstChoice = choicesNode.get(0);
                JsonNode messageNode = firstChoice.path("message");
                String content = messageNode.path("content").asText();
                return Mono.just(extractSqlFromResponse(content));
            } else {
                log.error("Invalid response format: no choices array or empty choices");
                return Mono.error(new ApiException("Invalid response format from LLM",
                        HttpStatus.INTERNAL_SERVER_ERROR));
            }
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
                return response.substring(start, end).trim();
            }
        }
        // Fallback: return the whole response if no code block is found
        return response.trim();
    }
}
