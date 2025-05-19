package com.example.backend.service;

import java.util.UUID;

import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.example.backend.config.LLMConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GigaChatAuthService {

    private final RestTemplate restTemplate;
    private final LLMConfig llmConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String accessToken;
    private long tokenExpirationTime;

    public String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpirationTime) {
            return accessToken;
        }
        return refreshToken();
    }

    private String refreshToken() {
        try {
            // Настройка SSL
            SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(null, (x509Certificates, s) -> true)
                .build();

            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                sslContext, NoopHostnameVerifier.INSTANCE);

            CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(socketFactory)
                .build();

            HttpComponentsClientHttpRequestFactory requestFactory = 
                new HttpComponentsClientHttpRequestFactory(httpClient);
            requestFactory.setConnectTimeout(5000);
            requestFactory.setReadTimeout(5000);

            RestTemplate sslRestTemplate = new RestTemplate(requestFactory);

            // Подготовка запроса
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("RqUID", UUID.randomUUID().toString());
            
            // Используем API ключ как есть, без дополнительного декодирования
            String authHeader = "Basic " + llmConfig.getApiKey();
            headers.set("Authorization", authHeader);
            
            log.debug("Using auth header: {}", authHeader);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("scope", "GIGACHAT_API_PERS");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

            log.debug("Requesting new access token from GigaChat. URL: {}, Headers: {}", 
                "https://ngw.devices.sberbank.ru:9443/api/v2/oauth", headers);

            ResponseEntity<String> response = sslRestTemplate.exchange(
                    "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.debug("Received response: {}", response.getBody());
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                accessToken = rootNode.path("access_token").asText();
                long expiresAt = rootNode.path("expires_at").asLong();
                tokenExpirationTime = expiresAt;
                
                log.debug("Successfully obtained new access token");
                return accessToken;
            }

            log.error("Failed to obtain access token. Status: {}, Response: {}", 
                response.getStatusCode(), response.getBody());
            throw new RuntimeException("Failed to obtain access token: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Error obtaining access token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to obtain access token", e);
        }
    }
} 