package com.smartparking.dto;

import java.time.Instant;

/** One entry in the live facility activity feed. */
public record ActivityEvent(
        String type,
        String slotNumber,
        String zone,
        int floor,
        String message,
        Instant at
) {
}
