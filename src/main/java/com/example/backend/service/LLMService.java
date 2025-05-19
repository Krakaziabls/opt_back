package com.example.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.backend.config.LLMConfig;
import com.example.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LLMService {

    private final RestTemplate restTemplate;
    private final LLMConfig llmConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String optimizeSqlQuery(String sqlQuery) {
        try {
            // Prepare request body for GigaChat API
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", llmConfig.getModel());

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", llmConfig.getSystemPrompt()));
            messages.add(Map.of("role", "user", "content", "Optimize this SQL query for PostgreSQL: " + sqlQuery));

            requestBody.put("messages", messages);
            requestBody.put("temperature", llmConfig.getTemperature());
            requestBody.put("max_tokens", llmConfig.getMaxTokens());

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmConfig.getApiKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Make API call
            ResponseEntity<String> response = restTemplate.exchange(
                    llmConfig.getApiUrl() + "/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // Parse response
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                JsonNode choicesNode = rootNode.path("choices");

                if (choicesNode.isArray() && choicesNode.size() > 0) {
                    JsonNode firstChoice = choicesNode.get(0);
                    JsonNode messageNode = firstChoice.path("message");
                    String content = messageNode.path("content").asText();

                    // Extract SQL code from response
                    return extractSqlFromResponse(content);
                }
            }

            throw new ApiException("Failed to get response from LLM", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Error calling LLM API: {}", e.getMessage());
            throw new ApiException("Error optimizing SQL query: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
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
