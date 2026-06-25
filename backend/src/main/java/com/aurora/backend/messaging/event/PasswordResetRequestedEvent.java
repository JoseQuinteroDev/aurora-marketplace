package com.aurora.backend.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user requests a password reset. Carries the raw reset token only
 * (NOT a full URL): the notification-service composes the link host from its own
 * config, so {@code event_outbox}/Kafka/the DLT never hold a ready-to-click link.
 */
public record PasswordResetRequestedEvent(
        String eventId,
        Instant occurredAt,
        UUID userId,
        String customerEmail,
        String customerName,
        String resetToken,
        int expiresInMinutes
) {

    public static PasswordResetRequestedEvent of(
            UUID userId,
            String customerEmail,
            String customerName,
            String resetToken,
            int expiresInMinutes
    ) {
        return new PasswordResetRequestedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                userId,
                customerEmail,
                customerName,
                resetToken,
                expiresInMinutes
        );
    }
}
