package com.smartparking.dto;

public record MyStatsResponse(
        long totalSessions,
        double totalSpent,
        boolean hasActiveReservation
) {
}
