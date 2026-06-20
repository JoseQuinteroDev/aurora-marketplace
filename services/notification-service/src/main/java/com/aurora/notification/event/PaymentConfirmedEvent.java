package com.aurora.notification.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Local view of the {@code aurora.payments.confirmed} event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentConfirmedEvent(
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
        String paymentMethod
) {
}
