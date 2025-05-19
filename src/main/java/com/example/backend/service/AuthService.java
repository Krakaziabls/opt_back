package com.example.backend.service;

import com.example.backend.exception.ApiException;
import com.example.backend.model.dto.AuthRequest;
import com.example.backend.model.dto.AuthResponse;
import com.example.backend.model.entity.User;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ApiException("Username already exists", HttpStatus.BAD_REQUEST);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getUsername() + "@example.com") // Simplified for MVP
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser = userRepository.save(user);

        String token = jwtTokenProvider.createToken(user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .username(savedUser.getUsername())
                .userId(savedUser.getId())
                .build();
    }

    public AuthResponse login(AuthRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
            String token = jwtTokenProvider.createToken(user.getUsername());
            return AuthResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .userId(user.getId())
                    .build();
        } catch (AuthenticationException e) {
            throw new ApiException("Invalid username/password", HttpStatus.UNAUTHORIZED);
        }
    }
}
