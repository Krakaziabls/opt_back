package com.example.backend.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.exception.ApiException;
import com.example.backend.model.dto.AuthRequest;
import com.example.backend.model.dto.AuthResponse;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication API")
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        log.debug("Registering user: username={}", request.getUsername());
        try {
            AuthResponse response = authService.register(request);
            log.info("User registered successfully: username={}", request.getUsername());
            return ResponseEntity.ok(response);
        } catch (ApiException e) {
            log.error("Registration failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during registration: {}", e.getMessage());
            throw new ApiException("Failed to register user", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Login with username and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.debug("Logging in user: username={}", request.getUsername());
        try {
            AuthResponse response = authService.login(request);
            log.info("User logged in successfully: username={}", request.getUsername());
            return ResponseEntity.ok(response);
        } catch (ApiException e) {
            log.error("Login failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during login: {}", e.getMessage());
            throw new ApiException("Failed to login", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user information")
    public ResponseEntity<AuthResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Fetching current user information");
        if (userDetails == null) {
            log.warn("User not authenticated for /auth/me");
            throw new ApiException("User not authenticated", HttpStatus.UNAUTHORIZED);
        }
        Long userId = getUserId(userDetails);
        log.info("Retrieved current user: username={}, userId={}", userDetails.getUsername(), userId);
        return ResponseEntity.ok(AuthResponse.builder()
                .username(userDetails.getUsername())
                .userId(userId)
                .build());
    }

    private Long getUserId(UserDetails userDetails) {
        if (userDetails instanceof CustomUserDetails) {
            return ((CustomUserDetails) userDetails).getUserId();
        }
        log.error("UserDetails is not an instance of CustomUserDetails");
        throw new IllegalStateException("UserDetails is not an instance of CustomUserDetails");
    }
}
