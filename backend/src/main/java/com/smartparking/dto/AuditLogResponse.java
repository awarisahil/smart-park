package com.smartparking.dto;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String actorEmail,
        String actorRole,
        String action,
        String target,
        String detail,
        String ip,
        Instant createdAt
) {
}
