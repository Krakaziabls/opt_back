package com.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Validated
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LLMConfig {

    @NotBlank(message = "LLM API URL must not be blank")
    @Value("${llm.api-url}")
    private String apiUrl;

    @NotBlank(message = "LLM API key must not be blank")
    @Value("${llm.api-key}")
    private String apiKey;

    @NotBlank(message = "LLM model must not be blank")
    @Value("${llm.model:GigaChat-Pro}")
    private String model;

    @NotBlank(message = "LLM system prompt must not be blank")
    @Value("${llm.system-prompt:You are a SQL optimization expert. Your task is to optimize SQL queries for PostgreSQL. Provide only the optimized query without any explanations.}")
    private String systemPrompt;

    @NotNull(message = "LLM temperature must not be null")
    @Min(value = 0, message = "LLM temperature must be greater than or equal to 0")
    @Max(value = 1, message = "LLM temperature must be less than or equal to 1")
    @Value("${llm.temperature:0.7}")
    private double temperature;

    @NotNull(message = "LLM max tokens must not be null")
    @Min(value = 1, message = "LLM max tokens must be greater than 0")
    @Value("${llm.max-tokens:2000}")
    private int maxTokens;

    @PostConstruct
    public void validate() {
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("LLM API URL is not configured");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM API key is not configured");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("LLM model is not configured");
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalStateException("LLM system prompt is not configured");
        }
        if (temperature < 0 || temperature > 1) {
            throw new IllegalStateException("LLM temperature must be between 0 and 1");
        }
        if (maxTokens <= 0) {
            throw new IllegalStateException("LLM max tokens must be positive");
        }
    }
}
