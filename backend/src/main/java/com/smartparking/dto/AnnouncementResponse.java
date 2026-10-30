package com.smartparking.dto;

import java.time.Instant;

public record AnnouncementResponse(
        String message,
        Instant at
) {
}
