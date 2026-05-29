package com.aurora.notification.store;

import java.time.Instant;

/**
 * Immutable record of a notification the service has processed. Held in an
 * in-memory store and exposed read-only via the REST API.
 */
public record NotificationRecord(
        String id,
        String type,
        String channel,
        String recipient,
        String subject,
        String relatedOrderNumber,
        String status,
        Instant createdAt
) {
}
