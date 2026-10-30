package com.smartparking.dto;

import com.smartparking.model.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String message,
        boolean read,
        Instant createdAt
) {
}
