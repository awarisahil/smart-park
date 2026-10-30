package com.smartparking.dto;

import com.smartparking.model.SlotType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReservationRequest(
        @NotBlank(message = "is required") @Size(min = 4, max = 15, message = "must be 4-15 characters") String vehicleNumber,
        String zone,
        SlotType preferredType,
        @Min(value = 1, message = "must be at least 1") @Max(value = 24, message = "must be at most 24") Integer expectedHours
) {
}
