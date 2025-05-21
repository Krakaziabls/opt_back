package com.example.backend.service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

@Slf4j
@Service
public class GigaChatAuthService {

    @Value("${gigachat.client-id}")
    private String clientId;

    @Value("${gigachat.client-secret}")
    private String clientSecret;

    @Value("${gigachat.auth-url}")
    private String authUrl;

    private String accessToken;
    private LocalDateTime tokenExpiration;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    public GigaChatAuthService() throws SSLException {
        SslContext sslContext = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        HttpClient httpClient = HttpClient.create()
                .secure(t -> t.sslContext(sslContext));

        this.webClient = WebClient.builder()
                .baseUrl(authUrl)
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .defaultHeader("RqUID", UUID.randomUUID().toString())
                .build();
    }

    public String getAccessToken() {
        if (accessToken == null || isTokenExpired()) {
            refreshToken();
        }
        return accessToken;
    }

    private boolean isTokenExpired() {
        return tokenExpiration == null || LocalDateTime.now().isAfter(tokenExpiration);
    }

    private void refreshToken() {
        try {
            String credentials = clientId + ":" + clientSecret;
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

            JsonNode response = webClient.post()
                    .header("Authorization", "Basic " + encodedCredentials)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("scope", "GIGACHAT_API_PERS"))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("GigaChat auth error: {}", errorBody);
                                        return Mono.error(new RuntimeException("Failed to authenticate with GigaChat: " + errorBody));
                                    }))
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.has("access_token")) {
                accessToken = response.get("access_token").asText();
                int expiresIn = response.get("expires_in").asInt();
                tokenExpiration = LocalDateTime.now().plusSeconds(expiresIn);
                log.info("Successfully refreshed GigaChat token");
            } else {
                log.error("Invalid response from GigaChat auth: {}", response);
                throw new RuntimeException("Invalid response from GigaChat auth service");
            }
        } catch (Exception e) {
            log.error("Failed to refresh GigaChat token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to refresh GigaChat token: " + e.getMessage(), e);
        }
    }

    public WebClient getWebClient() {
        return webClient;
    }
} 