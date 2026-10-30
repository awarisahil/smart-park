package com.smartparking.dto;

import com.smartparking.model.ReservationStatus;

import java.time.Instant;

public record ReservationResponse(
        Long id,
        String slotNumber,
        String zone,
        String vehicleNumber,
        String userName,
        String entryCode,
        ReservationStatus status,
        Instant reservedAt,
        Instant expiresAt,
        Instant entryTime,
        Instant exitTime,
        Double amount,
        int expectedHours,
        int extensionHours,
        boolean overstay,
        boolean synthetic,
        boolean passCovered
) {
}
