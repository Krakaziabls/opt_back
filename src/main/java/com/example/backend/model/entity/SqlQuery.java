package com.example.backend.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @ManyToOne
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
