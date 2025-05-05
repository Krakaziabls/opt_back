package com.example.backend.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.ChatDTO;
import com.example.backend.entity.Chat;
import com.example.backend.entity.User;
import com.example.backend.service.ChatService;

@RestController
@RequestMapping("/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ResponseEntity<List<ChatDTO>> getUserChats(@AuthenticationPrincipal User user) {
        List<Chat> chats = chatService.getUserChats(user);
        List<ChatDTO> chatDTOs = chats.stream()
                .map(chat -> {
                    ChatDTO dto = new ChatDTO();
                    dto.setId(chat.getId());
                    dto.setCreatedAt(chat.getCreatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(chatDTOs);
    }

    @PostMapping
    public ResponseEntity<ChatDTO> createChat(@AuthenticationPrincipal User user) {
        Chat chat = chatService.createChat(user);
        ChatDTO dto = new ChatDTO();
        dto.setId(chat.getId());
        dto.setCreatedAt(chat.getCreatedAt());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<ChatDTO> getChat(@PathVariable Long chatId, @AuthenticationPrincipal User user) {
        Chat chat = chatService.getChat(chatId, user);
        ChatDTO dto = new ChatDTO();
        dto.setId(chat.getId());
        dto.setCreatedAt(chat.getCreatedAt());
        return ResponseEntity.ok(dto);
    }
} 