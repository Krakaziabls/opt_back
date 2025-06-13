package com.example.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gigachat")
public class GigaChatConfig {
    private String clientId;
    private String clientSecret;
    private String authUrl;
    private Ssl ssl = new Ssl();

    @Getter
    @Setter
    public static class Ssl {
        private boolean trustAll = false;
    }
}
