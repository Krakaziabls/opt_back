package com.example.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.example.backend.model.dto.ChatDto;
import com.example.backend.model.dto.MessageDto;
import com.example.backend.service.ChatService;
import com.example.backend.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
@Tag(name = "Chats", description = "Chat management API")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    @Operation(summary = "Get all chats for the current user")
    public ResponseEntity<List<ChatDto>> getUserChats(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(chatService.getUserChats(userId));
    }

    @PostMapping
    @Operation(summary = "Create a new chat")
    public ResponseEntity<ChatDto> createChat(
            @Valid @RequestBody ChatDto chatDto,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(chatService.createChat(userId, chatDto));
    }

    @GetMapping("/{chatId}")
    @Operation(summary = "Get a specific chat")
    public ResponseEntity<ChatDto> getChat(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(chatService.getChat(chatId, userId));
    }

    @PostMapping("/{chatId}")
    public ResponseEntity<ChatDto> updateChat(
            @PathVariable Long chatId,
            @RequestBody ChatDto chatDto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(chatService.updateChat(chatId, userId, chatDto));
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteChat(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        chatService.deleteChat(chatId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{chatId}/archive")
    @Operation(summary = "Archive a chat")
    public ResponseEntity<Void> archiveChat(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        chatService.archiveChat(chatId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{chatId}/messages")
    @Operation(summary = "Get all messages in a chat")
    public ResponseEntity<List<MessageDto>> getChatMessages(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(chatService.getChatMessages(chatId, userId));
    }

    @PostMapping("/{chatId}/messages")
    public ResponseEntity<MessageDto> sendMessage(
            @PathVariable Long chatId,
            @RequestBody MessageDto messageDto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(chatService.sendMessage(chatId, userId, messageDto));
    }

    private Long getUserId(UserDetails userDetails) {
        if (userDetails instanceof CustomUserDetails) {
            return ((CustomUserDetails) userDetails).getUserId();
        }
        throw new IllegalStateException("UserDetails is not an instance of CustomUserDetails");
    }
}
