package com.example.backend.security;

import com.example.backend.model.entity.User;
import lombok.Getter;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

@Getter
public class CustomUserDetails extends org.springframework.security.core.userdetails.User implements UserDetails {
    private final Long userId;

    public CustomUserDetails(User user) {
        super(user.getUsername(), user.getPassword(), Collections.emptyList());
        this.userId = user.getId();
    }

}
