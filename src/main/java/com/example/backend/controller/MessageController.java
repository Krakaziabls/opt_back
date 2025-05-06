package com.example.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.backend.dto.MessageDTO;
import com.example.backend.entity.Chat;
import com.example.backend.entity.Message;
import com.example.backend.service.ChatService;
import com.example.backend.service.MessageService;

@RestController
@RequestMapping("/chats/{chatId}/messages")
public class MessageController {

    private final ChatService chatService;
    private final MessageService messageService;

    public MessageController(ChatService chatService, MessageService messageService) {
        this.chatService = chatService;
        this.messageService = messageService;
    }

    @GetMapping
    public ResponseEntity<List<MessageDTO>> getChatMessages(@PathVariable Long chatId) {
        Chat chat = chatService.getChatForCurrentUser(chatId);
        List<Message> messages = messageService.getChatMessages(chat);
        List<MessageDTO> messageDTOs = messages.stream()
                .map(MessageDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(messageDTOs);
    }

    @PostMapping
    public ResponseEntity<MessageDTO> createMessage(
            @PathVariable Long chatId,
            @RequestBody String content) {
        Chat chat = chatService.getChatForCurrentUser(chatId);
        Message message = messageService.createMessage(chat, Message.MessageSender.USER, content);
        return ResponseEntity.ok(MessageDTO.fromEntity(message));
    }
}
