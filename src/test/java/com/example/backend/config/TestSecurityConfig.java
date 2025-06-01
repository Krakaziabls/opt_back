package com.example.backend.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.example.backend.security.JwtTokenFilter;
import com.example.backend.security.JwtTokenProvider;
import com.example.backend.security.TestUserDetails;

@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(new AntPathRequestMatcher("/**")).permitAll()
            )
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Primary
    public UserDetailsService userDetailsService() {
        UserDetails userDetails = new TestUserDetails(1L, "testuser");
        return new InMemoryUserDetailsManager(userDetails);
    }

    @Bean
    @Primary
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Primary
    public JwtTokenProvider jwtTokenProvider(UserDetailsService userDetailsService) {
        return new JwtTokenProvider(userDetailsService);
    }

    @Bean
    @Primary
    public JwtTokenFilter jwtTokenFilter(JwtTokenProvider jwtTokenProvider) {
        return new JwtTokenFilter(jwtTokenProvider);
    }
} 