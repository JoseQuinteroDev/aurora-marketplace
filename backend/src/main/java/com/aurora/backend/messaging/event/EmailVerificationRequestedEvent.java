package com.aurora.backend.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a new account needs email verification. Carries the raw verification token
 * only (NOT a full URL): the notification-service composes the link host from its own config,
 * so {@code event_outbox}/Kafka/the DLT never hold a ready-to-click link.
 */
public record EmailVerificationRequestedEvent(
        String eventId,
        Instant occurredAt,
        UUID userId,
        String customerEmail,
        String customerName,
        String verificationToken,
        int expiresInMinutes
) {

    public static EmailVerificationRequestedEvent of(
            UUID userId,
            String customerEmail,
            String customerName,
            String verificationToken,
            int expiresInMinutes
    ) {
        return new EmailVerificationRequestedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                userId,
                customerEmail,
                customerName,
                verificationToken,
                expiresInMinutes
        );
    }
}
