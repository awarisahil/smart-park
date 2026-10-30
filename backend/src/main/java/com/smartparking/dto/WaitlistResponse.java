package com.smartparking.dto;

import com.smartparking.model.SlotType;
import com.smartparking.model.WaitlistStatus;

import java.time.Instant;

public record WaitlistResponse(
        Long id,
        String vehicleNumber,
        String zone,
        SlotType preferredType,
        WaitlistStatus status,
        long position,
        long totalQueued,
        Instant createdAt
) {
}
