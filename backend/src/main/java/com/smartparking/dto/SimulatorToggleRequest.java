package com.smartparking.dto;

import jakarta.validation.constraints.NotNull;

public record SimulatorToggleRequest(
        @NotNull(message = "is required") Boolean enabled
) {
}
