package com.example.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import lombok.Data;

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
    private int connectTimeout = 5000;
    private int readTimeout = 30000;
    
    // LM Studio configuration
    private String localApiUrl = "http://localhost:1234";
    private boolean localEnabled = false;
    private int localConnectTimeout = 10000;
    private int localReadTimeout = 60000;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    @Bean
    public RestTemplate localRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(localConnectTimeout);
        factory.setReadTimeout(localReadTimeout);
        return new RestTemplate(factory);
    }
}
