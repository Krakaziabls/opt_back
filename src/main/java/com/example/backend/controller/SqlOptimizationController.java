package com.example.backend.controller;

import com.example.backend.model.dto.SqlQueryRequest;
import com.example.backend.model.dto.SqlQueryResponse;
import com.example.backend.service.SqlOptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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
    public ResponseEntity<SqlQueryResponse> optimizeQuery(
            @Valid @RequestBody SqlQueryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(sqlOptimizationService.optimizeQuery(userId, request));
    }

    @GetMapping("/history/{chatId}")
    @Operation(summary = "Get SQL query history for a chat")
    public ResponseEntity<List<SqlQueryResponse>> getQueryHistory(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(sqlOptimizationService.getQueryHistory(chatId, userId));
    }

    private Long getUserId(UserDetails userDetails) {
        // In a real app, you would extract the user ID from the UserDetails
        // For simplicity, we'll assume the username is the user ID as a string
        return Long.parseLong(userDetails.getUsername());
    }
}
