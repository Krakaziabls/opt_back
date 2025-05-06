package com.example.backend.controller;

import com.example.backend.dto.OptimizeRequest;
import com.example.backend.dto.OptimizeResponse;
import com.example.backend.entity.Chat;
import com.example.backend.entity.OptimizedQuery;
import com.example.backend.entity.User;
import com.example.backend.service.ChatService;
import com.example.backend.service.SQLOptimizerService;
import com.example.backend.service.UserService;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sidebar")
public class SidebarController {

    private final ChatService chatService;
    private final UserService userService;

    @Autowired
    public SidebarController(ChatService chatService, UserService userService) {
        this.chatService = chatService;
        this.userService = userService;
    }

    @GetMapping
    public Map<String, Object> getSidebarData() {
        List<Chat> chats = chatService.getAllChatsForCurrentUser();
        User user = userService.getCurrentUser();
        // Отдаём данные в виде Map (ключи могут быть любыми, например, "chats" и "user")
        return Map.of("chats", chats, "user", user);
    }
}
