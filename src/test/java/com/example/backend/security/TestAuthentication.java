package com.example.backend.security;

import java.util.Collection;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class TestAuthentication extends UsernamePasswordAuthenticationToken {
    public TestAuthentication(TestUserDetails userDetails) {
        super(userDetails, null, userDetails.getAuthorities());
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return super.getAuthorities();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return super.getPrincipal();
    }
} 