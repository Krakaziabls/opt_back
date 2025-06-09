package com.example.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String content;
    private String sender;
    private Long chatId;
    private Long recipientId;
    private MessageType type;
    private String llmProvider;

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE
    }
} 