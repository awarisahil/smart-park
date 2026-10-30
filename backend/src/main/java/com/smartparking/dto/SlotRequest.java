package com.smartparking.dto;

import com.smartparking.model.SlotType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SlotRequest(
        @NotBlank(message = "is required") @Size(max = 20) String slotNumber,
        @NotBlank(message = "is required") @Size(max = 10) String zone,
        @DecimalMin(value = "0.0", message = "must be >= 0") Double hourlyRate,
        SlotType type,
        @Min(value = 1, message = "must be >= 1") Integer floor
) {
}
