package com.example.backend.model.dto;

import com.example.sqlopt.ast.QueryPlanResult;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlQueryResponse {

    private String id;
    private String originalQuery;
    private String optimizedQuery;
    private String createdAt;
    private MessageDto message;
    private QueryPlanResult originalPlan;
    private QueryPlanResult optimizedPlan;
    private Long executionTimeMs;
    private String optimizationRationale;
    private String performanceImpact;
    private String potentialRisks;
}
