package com.smartparking.dto;

import com.smartparking.model.SlotType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WaitlistRequest(
        @NotBlank(message = "is required") @Size(min = 4, max = 15) String vehicleNumber,
        String zone,
        SlotType preferredType
) {
}
