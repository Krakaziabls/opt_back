package com.example.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.model.dto.ChatDto;
import com.example.backend.model.dto.MessageDto;
import com.example.backend.model.entity.Chat;
import com.example.backend.model.entity.Message;
import com.example.backend.model.entity.User;
import com.example.backend.repository.ChatRepository;
import com.example.backend.repository.MessageRepository;
import com.example.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final DatabaseConnectionService databaseConnectionService;

    public List<ChatDto> getUserChats(Long userId) {
        List<Chat> chats = chatRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return chats.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ChatDto createChat(Long userId, ChatDto chatDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Chat chat = Chat.builder()
                .user(user)
                .title(chatDto.getTitle())
                .messages(new ArrayList<>())
                .build();

        Chat savedChat = chatRepository.save(chat);
        return mapToDto(savedChat);
    }

    public ChatDto getChat(Long chatId, Long userId) {
        Chat chat = chatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

        return mapToDto(chat);
    }

    public List<MessageDto> getChatMessages(Long chatId, Long userId) {
        // Verify chat belongs to user
        chatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

        List<Message> messages = messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
        return messages.stream()
                .map(this::mapToMessageDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void archiveChat(Long chatId, Long userId) {
        Chat chat = chatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

        chat.setArchived(true);
        chatRepository.save(chat);

        // Deactivate all database connections for this chat
        databaseConnectionService.deactivateConnectionsForChat(chatId);
    }

    private ChatDto mapToDto(Chat chat) {
        return ChatDto.builder()
                .id(chat.getId())
                .title(chat.getTitle())
                .createdAt(chat.getCreatedAt())
                .updatedAt(chat.getUpdatedAt())
                .archived(chat.isArchived())
                .build();
    }

    private MessageDto mapToMessageDto(Message message) {
        return MessageDto.builder()
                .id(message.getId())
                .chatId(message.getChat().getId())
                .content(message.getContent())
                .fromUser(message.isFromUser())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
