package com.example.sqlopt.controller;

import com.example.sqlopt.ast.QueryPlanResult;
import com.example.sqlopt.service.ASTService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ast")
@RequiredArgsConstructor
@Tag(name = "AST Analysis", description = "API для анализа планов запросов")
@Slf4j
public class ASTController {

    private final ASTService astService;

    @PostMapping("/analyze")
    @Operation(summary = "Анализ плана запроса")
    public ResponseEntity<QueryPlanResult> analyzeQueryPlan(
            @RequestParam String plan) {
        log.debug("Received query plan analysis request");
        try {
            QueryPlanResult result = astService.analyzeQueryPlan(plan);
            log.info("Query plan analyzed successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error analyzing query plan: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/metadata/{tableName}")
    @Operation(summary = "Обновление метаданных таблицы")
    public ResponseEntity<Void> updateTableMetadata(
            @PathVariable String tableName,
            @Valid @RequestBody Map<String, Object> metadata) {
        log.debug("Updating metadata for table: {}", tableName);
        try {
            astService.updateTableMetadata(tableName, metadata);
            log.info("Table metadata updated successfully: {}", tableName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error updating table metadata: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/metadata/{tableName}")
    @Operation(summary = "Получение метаданных таблицы")
    public ResponseEntity<Map<String, Object>> getTableMetadata(
            @PathVariable String tableName) {
        log.debug("Getting metadata for table: {}", tableName);
        try {
            Map<String, Object> metadata = astService.getTableMetadata(tableName);
            if (metadata != null) {
                log.info("Table metadata retrieved successfully: {}", tableName);
                return ResponseEntity.ok(metadata);
            } else {
                log.warn("Table metadata not found: {}", tableName);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting table metadata: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/metadata")
    @Operation(summary = "Очистка всех метаданных таблиц")
    public ResponseEntity<Void> clearTableMetadata() {
        log.debug("Clearing all table metadata");
        try {
            astService.clearTableMetadata();
            log.info("All table metadata cleared successfully");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error clearing table metadata: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
