package com.example.backend.model.dto;

import java.time.LocalDateTime;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

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

    @NotNull(message = "Chat ID is required")
    private Long chatId;

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "From user flag is required")
    private Boolean fromUser;

    private LocalDateTime createdAt;
}
