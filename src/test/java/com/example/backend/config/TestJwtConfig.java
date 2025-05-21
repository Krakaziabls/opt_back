package com.example.backend.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.backend.security.JwtTokenProvider;

@TestConfiguration
public class TestJwtConfig {

    @Bean
    @Primary
    public JwtTokenProvider jwtTokenProvider(UserDetailsService userDetailsService) {
        JwtTokenProvider provider = new JwtTokenProvider(userDetailsService);
        ReflectionTestUtils.setField(provider, "secretKey", "test-secret-key-for-testing-purposes-only-do-not-use-in-production");
        ReflectionTestUtils.setField(provider, "validityInMilliseconds", 86400000L);
        return provider;
    }
} 