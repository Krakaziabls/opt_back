package com.example.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import com.example.backend.config.TestSecurityConfig;
import com.example.backend.config.WebSocketConfig;
import com.example.backend.security.JwtTokenFilter;
import com.example.backend.security.JwtTokenProvider;

@WebMvcTest(WebSocketController.class)
@Import({JwtTokenFilter.class, WebSocketConfig.class, TestSecurityConfig.class})
public class WebSocketControllerTest {

    @Autowired
    private WebSocketController webSocketController;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser
    public void handleChatMessage_ValidMessage_ReturnsOk() {
        String message = "Test message";
        String expectedResponse = "Message received: " + message;

        String response = webSocketController.handleChatMessage(message);

        assertEquals(expectedResponse, response);
    }
} 