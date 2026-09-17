package com.lifeadmin.notification.api;

import java.time.Instant;

/** Response DTOs for in-app notifications (API_SPEC §5). */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            String id,
            String type,
            String title,
            String body,
            boolean read,
            String referenceType,
            String referenceId,
            Instant createdAt) {
    }

    public record UnreadCountResponse(long unread) {
    }
}
