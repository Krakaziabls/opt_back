package com.example.backend.model.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {

    private Long id;

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "From user flag is required")
    private Boolean fromUser;

    private LocalDateTime createdAt;

    private String llmProvider;

    private Long chatId;
}
