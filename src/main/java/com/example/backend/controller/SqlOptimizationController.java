package com.example.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.exception.ApiException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.model.dto.SqlQueryRequest;
import com.example.backend.model.dto.SqlQueryResponse;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.SqlOptimizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/sql")
@RequiredArgsConstructor
@Tag(name = "Оптимизация SQL", description = "API для оптимизации SQL-запросов")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class SqlOptimizationController {

    private final SqlOptimizationService sqlOptimizationService;

    @PostMapping("/optimize")
    @Operation(summary = "Оптимизировать SQL-запрос")
    public Mono<ResponseEntity<SqlQueryResponse>> optimizeQuery(
            @Valid @RequestBody SqlQueryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Received SQL optimization request: chatId={}, query={}, llm={}",
                request.getChatId(), request.getQuery(), request.getLlm());
        log.debug("User details: {}", userDetails);
        
        Long userId = getUserId(userDetails);
        log.info("User ID: {}", userId);
        
        return sqlOptimizationService.optimizeQuery(userId, request)
                .doOnSuccess(response -> log.info("SQL optimization successful: responseId={}", response.getId()))
                .doOnError(error -> log.error("SQL optimization failed: {}", error.getMessage(), error))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof ResourceNotFoundException) {
                        log.warn("Resource not found: {}", e.getMessage());
                        return Mono.just(ResponseEntity.notFound().build());
                    } else if (e instanceof ApiException) {
                        ApiException apiEx = (ApiException) e;
                        log.error("API error: status={}, message={}", apiEx.getStatus(), apiEx.getMessage());
                        return Mono.just(ResponseEntity.status(apiEx.getStatus()).body(null));
                    } else {
                        log.error("Unexpected error: {}", e.getMessage(), e);
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                    }
                });
    }

    @GetMapping("/history/{chatId}")
    @Operation(summary = "Получить историю SQL-запросов для чата")
    public ResponseEntity<List<SqlQueryResponse>> getQueryHistory(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Getting query history for chatId={}", chatId);
        Long userId = getUserId(userDetails);
        log.info("User ID: {}", userId);
        return ResponseEntity.ok(sqlOptimizationService.getQueryHistory(chatId, userId));
    }

    private Long getUserId(UserDetails userDetails) {
        if (userDetails instanceof CustomUserDetails) {
            return ((CustomUserDetails) userDetails).getUserId();
        }
        log.error("Invalid UserDetails type: {}", userDetails.getClass().getName());
        throw new IllegalStateException("UserDetails не является экземпляром CustomUserDetails");
    }
}
