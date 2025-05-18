package com.example.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

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

    private boolean fromUser;
    private LocalDateTime createdAt;
}
