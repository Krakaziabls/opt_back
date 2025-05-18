package com.example.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatDto {

    private Long id;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be less than 255 characters")
    private String title;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean archived;
}
