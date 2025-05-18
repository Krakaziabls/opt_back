package com.example.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConfigurationProperties(prefix = "llm")
@Data
public class LLMConfig {
    private String provider;
    private String apiUrl;
    private String apiKey;
    private String model;
    private int maxTokens;
    private double temperature;
    private String systemPrompt;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
