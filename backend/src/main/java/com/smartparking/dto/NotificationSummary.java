package com.smartparking.dto;

import java.util.List;

public record NotificationSummary(
        long unread,
        List<NotificationResponse> items
) {
}
