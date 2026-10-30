package com.smartparking.dto;

import com.smartparking.model.PassStatus;

import java.time.Instant;

public record PassResponse(
        Long id,
        String zone,
        Instant validFrom,
        Instant validUntil,
        double price,
        PassStatus status,
        boolean validNow
) {
}
