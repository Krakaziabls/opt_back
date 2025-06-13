package com.example.backend.controller;

import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.model.dto.ChatDto;
import com.example.backend.model.dto.MessageDto;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
@Tag(name = "Chats", description = "Chat management API")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService chatService;
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

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

    @PutMapping("/{chatId}")
    @Operation(summary = "Update a chat")
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
            @Valid @RequestBody MessageDto messageDto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        try {
            Long userId = getUserId(userDetails);
            log.debug("Received message request: chatId={}, userId={}, content={}", chatId, userId, messageDto.getContent());
            MessageDto response = chatService.sendMessage(chatId, userId, messageDto);
            log.info("Message sent successfully: chatId={}, messageId={}", chatId, response.getId());
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(MessageDto.builder()
                            .content("Chat not found or access denied")
                            .fromUser(false)
                            .build());
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MessageDto.builder()
                            .content("Invalid message: " + e.getMessage())
                            .fromUser(false)
                            .build());
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MessageDto.builder()
                            .content("An unexpected error occurred: " + e.getMessage())
                            .fromUser(false)
                            .build());
        }
    }

    private Long getUserId(UserDetails userDetails) {
        if (userDetails instanceof CustomUserDetails) {
            return ((CustomUserDetails) userDetails).getUserId();
        }
        throw new IllegalStateException("UserDetails is not an instance of CustomUserDetails");
    }
}
