// OptimizedQuery.java
package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "optimized_query")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizedQuery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "text")
    private String originalSql;
    @Column(columnDefinition = "text")
    private String optimizedSql;
    private Instant createdAt;
}
