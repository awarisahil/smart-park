package com.smartparking.dto;

import com.smartparking.model.SlotStatus;

import java.time.Instant;

/** Live session details for a single slot; plates are masked for non-staff viewers. */
public record SlotSessionResponse(
        SlotStatus status,
        String vehicleNumber,
        Instant entryTime,
        Instant holdExpiresAt,
        int expectedHours,
        int extensionHours,
        long elapsedMinutes,
        Double currentCost,
        String maintenanceNote
) {
}
