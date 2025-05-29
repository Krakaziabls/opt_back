package com.example.backend.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/sql")
@RequiredArgsConstructor
@Tag(name = "Оптимизация SQL", description = "API для оптимизации SQL-запросов")
@SecurityRequirement(name = "bearerAuth")
public class SqlOptimizationController {

    private final SqlOptimizationService sqlOptimizationService;

    @PostMapping("/optimize")
    @Operation(summary = "Оптимизировать SQL-запрос")
    public Mono<ResponseEntity<SqlQueryResponse>> optimizeQuery(
            @Valid @RequestBody SqlQueryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return sqlOptimizationService.optimizeQuery(userId, request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof ResourceNotFoundException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    } else if (e instanceof ApiException) {
                        ApiException apiEx = (ApiException) e;
                        return Mono.just(ResponseEntity.status(apiEx.getStatus()).body(null));
                    } else {
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                    }
                });
    }

    @GetMapping("/history/{chatId}")
    @Operation(summary = "Получить историю SQL-запросов для чата")
    public ResponseEntity<List<SqlQueryResponse>> getQueryHistory(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(sqlOptimizationService.getQueryHistory(chatId, userId));
    }

    private Long getUserId(UserDetails userDetails) {
        if (userDetails instanceof CustomUserDetails) {
            return ((CustomUserDetails) userDetails).getUserId();
        }
        throw new IllegalStateException("UserDetails не является экземпляром CustomUserDetails");
    }
}
