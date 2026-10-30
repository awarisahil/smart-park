package com.smartparking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
        @NotBlank(message = "is required") @Size(max = 60) String label,
        @NotBlank(message = "is required") @Size(min = 4, max = 15) String vehicleNumber
) {
}
