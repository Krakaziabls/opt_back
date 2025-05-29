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
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class GigaChatAuthService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 1000;

    @Value("${gigachat.client-id}")
    private String clientId;

    @Value("${gigachat.client-secret}")
    private String clientSecret;

    @Value("${gigachat.auth-url}")
    private String authUrl;

    @Value("${llm.api-url}")
    private String apiUrl;

    @Value("${gigachat.ssl.trust-all:false}")
    private boolean trustAllCertificates;

    private final AtomicReference<String> tokenRef = new AtomicReference<>();
    private final AtomicReference<Instant> tokenExpiryRef = new AtomicReference<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebClient authWebClient;
    private WebClient.Builder apiWebClientBuilder;

    @PostConstruct
    public void init() throws Exception {
        validateConfiguration();
        configureWebClients();
    }

    private void validateConfiguration() {
        if (authUrl == null || authUrl.isBlank()) {
            throw new IllegalStateException("GigaChat authUrl is not configured");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("GigaChat apiUrl is not configured");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("GigaChat clientId is not configured");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("GigaChat clientSecret is not configured");
        }
    }

    private void configureWebClients() throws Exception {
        log.info("Initializing GigaChat auth client with URL: {}", authUrl);
        log.info("Initializing GigaChat API client with URL: {}", apiUrl);

        HttpClient httpClient = createHttpClient();
        
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

    private HttpClient createHttpClient() throws Exception {
        HttpClient httpClient = HttpClient.create().keepAlive(true);
        
        if (trustAllCertificates) {
            log.warn("Using insecure SSL configuration - trustAllCertificates is enabled");
            SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            httpClient = httpClient.secure(t -> t.sslContext(sslContext));
        }
        
        return httpClient;
    }

    public Mono<String> getToken() {
        String currentToken = tokenRef.get();
        if (currentToken == null || isTokenExpired()) {
            return refreshToken()
                    .doOnNext(token -> tokenRef.set(token))
                    .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, java.time.Duration.ofMillis(RETRY_DELAY_MS))
                            .doBeforeRetry(signal -> log.warn("Retrying token refresh, attempt: {}", signal.totalRetries() + 1))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> 
                                new RuntimeException("Failed to refresh token after " + MAX_RETRY_ATTEMPTS + " attempts")));
        }
        return Mono.just(currentToken);
    }

    private boolean isTokenExpired() {
        Instant expiry = tokenExpiryRef.get();
        return expiry == null || Instant.now().isAfter(expiry.minusSeconds(300)); // Обновление за 5 минут до истечения
    }

    private Mono<String> refreshToken() {
        try {
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
