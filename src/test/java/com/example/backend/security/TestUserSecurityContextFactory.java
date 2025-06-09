package com.example.backend.security;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class TestUserSecurityContextFactory implements WithSecurityContextFactory<WithTestUser> {
    @Override
    public SecurityContext createSecurityContext(WithTestUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        TestUserDetails userDetails = new TestUserDetails(annotation.userId(), annotation.username());
        context.setAuthentication(new TestAuthentication(userDetails));
        return context;
    }
} 