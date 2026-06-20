package com.aurora.notification.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Local view of the {@code aurora.payments.failed} event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedEvent(
        String eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        String customerEmail,
        String customerName,
        String customerPhone,
        String notificationChannel,
        BigDecimal amount,
        String currency,
        String reason
) {
}
