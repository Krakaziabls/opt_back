package com.example.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlQueryResponse {

    private Long id;
    private String originalQuery;
    private String optimizedQuery;
    private Long executionTimeMs;
    private LocalDateTime createdAt;
    private MessageDto message;
}
