package com.smartparking.dto;

import com.smartparking.model.SlotType;
import jakarta.validation.constraints.*;

import java.time.Instant;

public record BookingRequest(
        @NotBlank(message = "is required") @Size(min = 4, max = 15) String vehicleNumber,
        String zone,
        SlotType slotType,
        @NotNull(message = "is required") Instant startTime,
        @Min(1) @Max(12) Integer expectedHours
) {
}
