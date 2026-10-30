package com.smartparking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExtensionRequest(
        @NotNull(message = "is required") @Min(value = 1, message = "must be at least 1") @Max(value = 12, message = "must be at most 12") Integer additionalHours
) {
}
