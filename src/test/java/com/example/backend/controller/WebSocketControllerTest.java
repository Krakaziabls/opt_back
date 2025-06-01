package com.example.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import com.example.backend.config.TestConfig;
import com.example.backend.config.TestSecurityConfig;
import com.example.backend.config.TestWebSocketConfig;
import com.example.backend.model.ChatMessage;
import com.example.backend.model.dto.MessageDto;
import com.example.backend.service.ChatService;

@WebMvcTest(WebSocketController.class)
@Import({TestConfig.class, TestSecurityConfig.class, TestWebSocketConfig.class})
public class WebSocketControllerTest {

    @Autowired
    private WebSocketController webSocketController;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @MockBean
    private ChatService chatService;

    @Test
    @WithMockUser(username = "testuser")
    public void sendMessage_ValidMessage_SendsToTopic() {
        // Arrange
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent("Test message");
        chatMessage.setChatId(123L);

        SimpMessageHeaderAccessor headerAccessor = mock(SimpMessageHeaderAccessor.class);
        when(headerAccessor.getUser()).thenReturn(() -> "1"); // ID пользователя из TestUserDetails

        MessageDto messageDto = MessageDto.builder()
                .content("Test message")
                .fromUser(true)
                .build();

        when(chatService.sendMessage(eq(123L), eq(1L), any(MessageDto.class))).thenReturn(messageDto);

        // Act
        webSocketController.sendMessage(chatMessage, headerAccessor);

        // Assert
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/123"), any(ChatMessage.class));
        verify(chatService).sendMessage(eq(123L), eq(1L), any(MessageDto.class));
    }
}
