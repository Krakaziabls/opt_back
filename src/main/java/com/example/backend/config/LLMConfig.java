package com.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;

@Getter
@Configuration
public class LLMConfig {

    @Value("${llm.api-url}")
    private String apiUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.system-prompt}")
    private String systemPrompt;

    @Value("${llm.temperature}")
    private double temperature;

    @Value("${llm.max-tokens}")
    private int maxTokens;
}
