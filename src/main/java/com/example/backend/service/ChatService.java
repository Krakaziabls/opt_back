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
    private final UserService userService;

    public ChatService(ChatRepository chatRepository, UserService userService) {
        this.chatRepository = chatRepository;
        this.userService = userService;
    }

    // Создание нового чата для текущего пользователя
    @Transactional
    public Chat createChat() {
        User currentUser = userService.getCurrentUser();
        Chat chat = new Chat();
        chat.setUser(currentUser);
        // Имя по умолчанию можно задать, если хочешь
        chat.setName("Новый чат");
        return chatRepository.save(chat);
    }

    // Получение всех чатов текущего пользователя
    @Transactional(readOnly = true)
    public List<Chat> getAllChatsForCurrentUser() {
        User currentUser = userService.getCurrentUser();
        return chatRepository.findByUserOrderByCreatedAtDesc(currentUser);
    }

    // Получение чата по ID для текущего пользователя
    @Transactional(readOnly = true)
    public Chat getChatForCurrentUser(Long chatId) {
        User currentUser = userService.getCurrentUser();
        return chatRepository.findByIdAndUser(chatId, currentUser)
                .orElseThrow(() -> new RuntimeException("Chat not found"));
    }

    // Переименование чата
    @Transactional
    public Chat updateChat(Long chatId, String newName) {
        Chat chat = getChatForCurrentUser(chatId);
        chat.setName(newName);
        return chatRepository.save(chat);
    }


    // Удаление чата
    @Transactional
    public void deleteChat(Long chatId) {
        Chat chat = getChatForCurrentUser(chatId);
        chatRepository.delete(chat);
    }
}
