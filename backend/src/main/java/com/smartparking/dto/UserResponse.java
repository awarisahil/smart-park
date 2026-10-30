package com.smartparking.dto;

import com.smartparking.model.Role;

import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        boolean enabled,
        Instant createdAt
) {
}
