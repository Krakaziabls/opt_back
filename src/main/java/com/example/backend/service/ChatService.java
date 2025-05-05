package com.example.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.entity.Chat;
import com.example.backend.entity.User;
import com.example.backend.repository.ChatRepository;

@Service
public class ChatService {

    private final ChatRepository chatRepository;

    public ChatService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    @Transactional
    public Chat createChat(User user) {
        Chat chat = new Chat();
        chat.setUser(user);
        return chatRepository.save(chat);
    }

    @Transactional(readOnly = true)
    public List<Chat> getUserChats(User user) {
        return chatRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public Chat getChat(Long chatId, User user) {
        return chatRepository.findByIdAndUser(chatId, user)
                .orElseThrow(() -> new RuntimeException("Chat not found"));
    }
} 