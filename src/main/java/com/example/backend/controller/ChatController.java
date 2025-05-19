package com.example.backend.controller;

import com.example.backend.model.dto.ChatDto;
import com.example.backend.model.dto.MessageDto;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

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

    @GetMapping("/{chatId}/messages")
    @Operation(summary = "Get all messages in a chat")
    public ResponseEntity<List<MessageDto>> getChatMessages(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(chatService.getChatMessages(chatId, userId));
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

    private Long getUserId(UserDetails userDetails) {
        if (userDetails instanceof CustomUserDetails) {
            return ((CustomUserDetails) userDetails).getUserId();
        }
        throw new IllegalStateException("UserDetails is not an instance of CustomUserDetails");
    }
}
