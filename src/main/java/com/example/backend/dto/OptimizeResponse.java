// OptimizeResponse.java
package com.example.backend.dto;

import lombok.Data;

@Data
public class OptimizeResponse {
    private String originalSql;
    private String optimizedSql;
}
