package com.example.backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WebSocketController.class)
public class WebSocketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Test
    public void handleChatMessage_ValidMessage_ReturnsOk() throws Exception {
        String message = "Test message";
        String expectedResponse = "Message received: " + message;

        mockMvc.perform(post("/chat")
                .contentType("text/plain")
                .content(message))
                .andExpect(status().isOk());

        verify(messagingTemplate).convertAndSend(eq("/topic/messages"), eq(expectedResponse));
    }
} 