package com.smartparking.dto;

import com.smartparking.model.Role;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
        @NotNull(message = "is required") Role role
) {
}
