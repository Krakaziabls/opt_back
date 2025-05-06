package com.example.backend.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Data
public class ChatDTO {
    private Long id;
    private LocalDateTime createdAt;
    // <-- Сеттер для name
    // <-- Геттер для name
    private String name; // <-- ВАЖНО: поле name должно быть!

}
