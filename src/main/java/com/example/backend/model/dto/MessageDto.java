package com.example.backend.model.dto;

import com.example.backend.model.entity.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    private Long chatId;

    private String llmProvider;

    public static MessageDto fromEntity(Message message) {
        return MessageDto.builder()
                .id(message.getId())
                .content(message.getContent())
                .fromUser(message.isFromUser())
                .createdAt(message.getCreatedAt())
                .chatId(message.getChat().getId())
                .build();
    }
}
