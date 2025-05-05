package com.example.backend.dto;

import java.time.LocalDateTime;

import com.example.backend.entity.Message;

import lombok.Data;

@Data
public class MessageDTO {
    private Long id;
    private Message.MessageSender sender;
    private String content;
    private LocalDateTime createdAt;

    public static MessageDTO fromEntity(Message message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setSender(message.getSender());
        dto.setContent(message.getContent());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }
} 