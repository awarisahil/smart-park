package com.smartparking.dto;

import jakarta.validation.constraints.NotNull;

public record EnabledUpdateRequest(
        @NotNull(message = "is required") Boolean enabled
) {
}
