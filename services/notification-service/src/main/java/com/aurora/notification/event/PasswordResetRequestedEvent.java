package com.aurora.notification.event;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Local view of the {@code aurora.auth.password-reset-requested} event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PasswordResetRequestedEvent(
        String eventId,
        Instant occurredAt,
        UUID userId,
        String customerEmail,
        String customerName,
        String resetToken,
        int expiresInMinutes
) {
}
