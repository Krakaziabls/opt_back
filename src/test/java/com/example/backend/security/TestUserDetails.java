package com.example.backend.security;

import com.example.backend.model.entity.User;

public class TestUserDetails extends CustomUserDetails {
    private final String username;

    public TestUserDetails(Long userId, String username) {
        super(createTestUser(userId, username));
        this.username = username;
    }

    private static User createTestUser(Long userId, String username) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setPassword("password");
        return user;
    }

    @Override
    public String getUsername() {
        return username;
    }
} 