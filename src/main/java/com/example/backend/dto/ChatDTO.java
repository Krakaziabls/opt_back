package com.example.backend.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ChatDTO {
    private Long id;
    private LocalDateTime createdAt;
} 