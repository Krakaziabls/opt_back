package com.example.backend.service;

import java.util.Base64;

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
            
            // Декодируем API ключ из base64
            String decodedApiKey = new String(Base64.getDecoder().decode(llmConfig.getApiKey()));
            log.debug("Decoded API key: {}", decodedApiKey);
            
            headers.setBasicAuth(decodedApiKey);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

            log.debug("Requesting new access token from GigaChat. URL: {}, Headers: {}", 
                llmConfig.getApiUrl() + "/auth", headers);

            ResponseEntity<String> response = sslRestTemplate.exchange(
                    llmConfig.getApiUrl() + "/auth",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.debug("Received response: {}", response.getBody());
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                accessToken = rootNode.path("access_token").asText();
                int expiresIn = rootNode.path("expires_in").asInt();
                tokenExpirationTime = System.currentTimeMillis() + (expiresIn - 60) * 1000L;
                
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