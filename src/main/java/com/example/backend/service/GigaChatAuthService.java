package com.example.backend.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Service
public class GigaChatAuthService {

    @Value("${gigachat.client-id}")
    private String clientId;

    @Value("${gigachat.client-secret}")
    private String clientSecret;

    @Value("${gigachat.auth-url}")
    private String authUrl;

    @Value("${llm.api-url}")
    private String apiUrl;

    private final AtomicReference<String> tokenRef = new AtomicReference<>();
    private final AtomicReference<Instant> tokenExpiryRef = new AtomicReference<>();

    private WebClient authWebClient;
    private WebClient.Builder apiWebClientBuilder;

    @PostConstruct
    public void init() throws Exception {
        if (authUrl == null || authUrl.isBlank()) {
            throw new IllegalStateException("GigaChat authUrl is not configured");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("GigaChat apiUrl is not configured");
        }

        log.info("Initializing GigaChat auth client with URL: {}", authUrl);
        log.info("Initializing GigaChat API client with URL: {}", apiUrl);

        // Настройка безопасного SSL для production
        SslContext sslContext = SslContextBuilder.forClient().build();
        HttpClient httpClient = HttpClient.create()
                .secure(t -> t.sslContext(sslContext))
                .keepAlive(true);

        this.authWebClient = WebClient.builder()
                .baseUrl(authUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("RqUID", UUID.randomUUID().toString())
                .build();

        this.apiWebClientBuilder = WebClient.builder()
                .baseUrl(apiUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    public Mono<String> getToken() {
        String currentToken = tokenRef.get();
        if (currentToken == null || isTokenExpired()) {
            return refreshToken().doOnNext(token -> tokenRef.set(token));
        }
        return Mono.just(currentToken);
    }

    private boolean isTokenExpired() {
        Instant expiry = tokenExpiryRef.get();
        return expiry == null || Instant.now().isAfter(expiry.minusSeconds(300)); // Обновление за 5 минут до истечения
    }

    private Mono<String> refreshToken() {
        if (authUrl == null || authUrl.isBlank()) {
            log.error("authUrl is empty or null");
            return Mono.error(new IllegalStateException("GigaChat authUrl is not configured"));
        }
        try {
            if (clientId == null || clientSecret == null) {
                return Mono.error(new IllegalStateException("GigaChat credentials are not configured"));
            }

            String credentials = clientId.trim() + ":" + clientSecret.trim();
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8))
                    .replaceAll("\\s+", "");

            log.info("Attempting to authenticate with GigaChat API");
            log.debug("Auth URL: {}", authUrl);

            String authHeader = "Basic " + encodedCredentials.trim();

            return authWebClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Authorization", authHeader)
                    .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                            .with("scope", "GIGACHAT_API_PERS"))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("GigaChat auth error: status={}, body={}, headers={}",
                                                clientResponse.statusCode(),
                                                errorBody,
                                                clientResponse.headers().asHttpHeaders());
                                        return Mono.error(new RuntimeException(
                                                String.format("Failed to authenticate with GigaChat: status=%d, body=%s",
                                                        clientResponse.statusCode().value(), errorBody)));
                                    }))
                    .bodyToMono(JsonNode.class)
                    .flatMap(response -> {
                        if (response.has("access_token")) {
                            String token = response.get("access_token").asText();
                            int expiresIn = response.get("expires_in").asInt();
                            Instant expiry = Instant.now().plusSeconds(expiresIn);
                            tokenExpiryRef.set(expiry);
                            log.info("Successfully refreshed GigaChat token, expires in {} seconds", expiresIn);
                            return Mono.just(token);
                        } else {
                            log.error("Invalid response from GigaChat auth: {}", response);
                            return Mono.error(new RuntimeException("Invalid response from GigaChat auth service"));
                        }
                    })
                    .doOnError(e -> log.error("Error during token refresh: {}", e.getMessage(), e));
        } catch (Exception e) {
            log.error("Failed to refresh GigaChat token: {}", e.getMessage(), e);
            return Mono.error(new RuntimeException("Failed to refresh GigaChat token: " + e.getMessage(), e));
        }
    }

    public Mono<WebClient> getWebClient() {
        return getToken().map(token -> apiWebClientBuilder
                .build()
                .mutate()
                .defaultHeader("Authorization", "Bearer " + token)
                .build());
    }
}
