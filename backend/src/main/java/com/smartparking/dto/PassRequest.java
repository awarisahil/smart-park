package com.smartparking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PassRequest(
        @Pattern(regexp = "^[A-F]?$", message = "must be a zone letter or empty for all zones") String zone,
        @NotNull(message = "is required") @Min(1) @Max(12) Integer months
) {
}
