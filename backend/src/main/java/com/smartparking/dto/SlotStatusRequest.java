package com.smartparking.dto;

import com.smartparking.model.SlotStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SlotStatusRequest(
        @NotNull(message = "is required") SlotStatus status,
        @Size(max = 120) String note
) {
}
