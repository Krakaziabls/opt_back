package com.example.backend.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.backend.dto.ChatDTO;
import com.example.backend.entity.Chat;
import com.example.backend.service.ChatService;

@RestController
@RequestMapping("/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ResponseEntity<List<ChatDTO>> getUserChats() {
        List<Chat> chats = chatService.getAllChatsForCurrentUser();
        List<ChatDTO> chatDTOs = chats.stream()
                .map(chat -> {
                    ChatDTO dto = new ChatDTO();
                    dto.setId(chat.getId());
                    dto.setCreatedAt(chat.getCreatedAt());
                    dto.setName(chat.getName());
                    return dto;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(chatDTOs);
    }

    @PostMapping
    public ResponseEntity<ChatDTO> createChat() {
        Chat chat = chatService.createChat();
        ChatDTO dto = new ChatDTO();
        dto.setId(chat.getId());
        dto.setCreatedAt(chat.getCreatedAt());
        dto.setName(chat.getName());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<ChatDTO> getChat(@PathVariable Long chatId) {
        Chat chat = chatService.getChatForCurrentUser(chatId);
        ChatDTO dto = new ChatDTO();
        dto.setId(chat.getId());
        dto.setCreatedAt(chat.getCreatedAt());
        dto.setName(chat.getName());
        return ResponseEntity.ok(dto);
    }

    // Новый эндпоинт для переименования чата
    @PatchMapping("/{chatId}")
    public ResponseEntity<ChatDTO> renameChat(@PathVariable Long chatId, @RequestParam String name) {
        Chat updatedChat = chatService.updateChat(chatId, name);
        ChatDTO dto = new ChatDTO();
        dto.setId(updatedChat.getId());
        dto.setCreatedAt(updatedChat.getCreatedAt());
        dto.setName(updatedChat.getName());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteChat(@PathVariable Long chatId) {
        chatService.deleteChat(chatId);
        return ResponseEntity.noContent().build();
    }
}
