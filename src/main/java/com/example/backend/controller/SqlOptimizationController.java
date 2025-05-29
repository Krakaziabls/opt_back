package com.example.backend.controller;

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
@Tag(name = "SQL Optimization", description = "SQL query optimization API")
@SecurityRequirement(name = "bearerAuth")
public class SqlOptimizationController {

    private final SqlOptimizationService sqlOptimizationService;

    @PostMapping("/optimize")
    @Operation(summary = "Optimize an SQL query")
    public Mono<ResponseEntity<SqlQueryResponse>> optimizeQuery(
            @Valid @RequestBody SqlQueryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return sqlOptimizationService.optimizeQuery(userId, request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()));
    }

    @GetMapping("/history/{chatId}")
    @Operation(summary = "Get SQL query history for a chat")
    public ResponseEntity<List<SqlQueryResponse>> getQueryHistory(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(sqlOptimizationService.getQueryHistory(chatId, userId));
    }

    private Long getUserId(UserDetails userDetails) throws IllegalStateException {
        if (userDetails instanceof CustomUserDetails) {
            return ((CustomUserDetails) userDetails).getUserId();
        }
        throw new IllegalStateException("UserDetails is not an instance of CustomUserDetails");
    }
}
