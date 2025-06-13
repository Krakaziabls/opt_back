package com.example.backend.model.entity;

import com.example.sqlopt.ast.QueryPlanResult;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "sql_queries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SqlQuery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "original_query", nullable = false, columnDefinition = "TEXT")
    private String originalQuery;

    @Column(name = "optimized_query", columnDefinition = "TEXT")
    private String optimizedQuery;

    @ManyToOne
    @JoinColumn(name = "database_connection_id")
    private DatabaseConnection databaseConnection;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Type(JsonBinaryType.class)
    @Column(name = "original_plan", columnDefinition = "jsonb")
    private QueryPlanResult originalPlan;

    @Type(JsonBinaryType.class)
    @Column(name = "optimized_plan", columnDefinition = "jsonb")
    private QueryPlanResult optimizedPlan;

    @Type(JsonBinaryType.class)
    @Column(name = "tables_metadata", columnDefinition = "jsonb")
    private Map<String, Map<String, Object>> tablesMetadata;

    @Column(name = "optimization_rationale", columnDefinition = "TEXT")
    private String optimizationRationale;

    @Column(name = "performance_impact", columnDefinition = "TEXT")
    private String performanceImpact;

    @Column(name = "potential_risks", columnDefinition = "TEXT")
    private String potentialRisks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
