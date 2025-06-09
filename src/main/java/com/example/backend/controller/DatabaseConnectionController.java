package com.example.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.model.dto.DatabaseConnectionDto;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.DatabaseConnectionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/connections")
@RequiredArgsConstructor
@Tag(name = "Database Connections", description = "Database connection management API")
@SecurityRequirement(name = "bearerAuth")
public class DatabaseConnectionController {

    private final DatabaseConnectionService databaseConnectionService;

    @GetMapping("/chat/{chatId}")
    @Operation(summary = "Get all database connections for a chat")
    public ResponseEntity<List<DatabaseConnectionDto>> getConnectionsForChat(@PathVariable Long chatId) {
        return ResponseEntity.ok(databaseConnectionService.getConnectionsForChat(chatId));
    }

    @PostMapping
    @Operation(summary = "Create a new database connection")
    public ResponseEntity<DatabaseConnectionDto> createConnection(
            @Valid @RequestBody DatabaseConnectionDto connectionDto,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(databaseConnectionService.createConnection(userId, connectionDto));
    }

    @PostMapping("/test")
    @Operation(summary = "Test a database connection without saving it")
    public ResponseEntity<Map<String, Boolean>> testConnection(
            @Valid @RequestBody DatabaseConnectionDto connectionDto) {
        boolean success = databaseConnectionService.testConnection(connectionDto);
        return ResponseEntity.ok(Map.of("success", success));
    }

    @PostMapping("/{connectionId}/close")
    @Operation(summary = "Close a database connection")
    public ResponseEntity<Void> closeConnection(@PathVariable Long connectionId) {
        databaseConnectionService.closeConnection(connectionId);
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(UserDetails userDetails) {
        if (userDetails instanceof CustomUserDetails) {
            return ((CustomUserDetails) userDetails).getUserId();
        }
        throw new IllegalStateException("UserDetails is not an instance of CustomUserDetails");
    }
}
