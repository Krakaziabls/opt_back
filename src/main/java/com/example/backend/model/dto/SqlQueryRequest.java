package com.example.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlQueryRequest {

    @NotNull(message = "Chat ID is required")
    private Long chatId;

    @NotBlank(message = "SQL query is required")
    private String query;

    private Long databaseConnectionId;
}
