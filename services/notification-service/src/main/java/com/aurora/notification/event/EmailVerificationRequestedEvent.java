package com.aurora.notification.event;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Local view of the {@code aurora.auth.email-verification-requested} event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailVerificationRequestedEvent(
        String eventId,
        Instant occurredAt,
        UUID userId,
        String customerEmail,
        String customerName,
        String verificationToken,
        int expiresInMinutes
) {
}
