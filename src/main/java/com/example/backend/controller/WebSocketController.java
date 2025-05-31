package com.example.backend.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.backend.model.ChatMessage;
import com.example.backend.model.dto.MessageDto;
import com.example.backend.service.ChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        log.info("Received message: {}", chatMessage);
        String username = headerAccessor.getUser().getName();
        chatMessage.setSender(username);
        
        // Сохраняем сообщение в базе данных
        MessageDto messageDto = MessageDto.builder()
                .content(chatMessage.getContent())
                .fromUser(true)
                .build();
        
        chatService.sendMessage(chatMessage.getChatId(), Long.parseLong(username), messageDto);
        
        // Отправляем сообщение всем подписчикам чата
        messagingTemplate.convertAndSend("/topic/chat/" + chatMessage.getChatId(), chatMessage);
    }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        log.info("User added: {}", chatMessage);
        headerAccessor.getSessionAttributes().put("username", chatMessage.getSender());
        headerAccessor.getSessionAttributes().put("chatId", chatMessage.getChatId());
        
        // Отправляем уведомление о присоединении пользователя
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        messagingTemplate.convertAndSend("/topic/chat/" + chatMessage.getChatId(), chatMessage);
    }
}
