package com.example.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.SQLQueryDTO;
import com.example.backend.entity.Chat;
import com.example.backend.entity.SQLQuery;
import com.example.backend.entity.User;
import com.example.backend.service.ChatService;
import com.example.backend.service.SQLQueryService;

@RestController
@RequestMapping("/sql")
public class SQLQueryController {

    private final ChatService chatService;
    private final SQLQueryService sqlQueryService;

    public SQLQueryController(ChatService chatService, SQLQueryService sqlQueryService) {
        this.chatService = chatService;
        this.sqlQueryService = sqlQueryService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<SQLQueryDTO> analyzeQuery(
            @RequestParam Long chatId,
            @RequestBody String queryText,
            @AuthenticationPrincipal User user) {
        Chat chat = chatService.getChat(chatId, user);
        SQLQuery query = sqlQueryService.analyzeQuery(chat, queryText);
        return ResponseEntity.ok(SQLQueryDTO.fromEntity(query));
    }

    @GetMapping("/queries/{queryId}")
    public ResponseEntity<SQLQueryDTO> getQuery(
            @PathVariable Long queryId,
            @RequestParam Long chatId,
            @AuthenticationPrincipal User user) {
        Chat chat = chatService.getChat(chatId, user);
        SQLQuery query = sqlQueryService.getQuery(queryId, chat);
        return ResponseEntity.ok(SQLQueryDTO.fromEntity(query));
    }

    @GetMapping("/chats/{chatId}/queries")
    public ResponseEntity<List<SQLQueryDTO>> getChatQueries(
            @PathVariable Long chatId,
            @AuthenticationPrincipal User user) {
        Chat chat = chatService.getChat(chatId, user);
        List<SQLQuery> queries = sqlQueryService.getChatQueries(chat);
        List<SQLQueryDTO> queryDTOs = queries.stream()
                .map(SQLQueryDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(queryDTOs);
    }
} 