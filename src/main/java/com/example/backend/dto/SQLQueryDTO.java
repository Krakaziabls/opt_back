package com.example.backend.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SQLQueryDTO {
    private Long id;
    private String queryText;
    private String analysisResult;
    private String astTree;
    private String optimizedQuery;
    private String errorMessage;
    private LocalDateTime createdAt;

    public static SQLQueryDTO fromEntity(com.example.backend.entity.SQLQuery query) {
        SQLQueryDTO dto = new SQLQueryDTO();
        dto.setId(query.getId());
        dto.setQueryText(query.getQueryText());
        dto.setAnalysisResult(query.getAnalysisResult());
        dto.setAstTree(query.getAstTree());
        dto.setOptimizedQuery(query.getOptimizedQuery());
        dto.setErrorMessage(query.getErrorMessage());
        dto.setCreatedAt(query.getCreatedAt());
        return dto;
    }
} 