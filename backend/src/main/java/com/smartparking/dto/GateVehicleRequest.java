package com.smartparking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GateVehicleRequest(
        @NotBlank(message = "is required") @Size(min = 4, max = 15) String vehicleNumber
) {
}
