package com.aurora.backend.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.user.notification.NotificationChannel;

/** Published when a payment attempt for an order fails. */
public record PaymentFailedEvent(
        String eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        String customerEmail,
        String customerName,
        String customerPhone,
        NotificationChannel notificationChannel,
        BigDecimal amount,
        String currency,
        String reason
) {

    public static PaymentFailedEvent of(
            UUID orderId,
            String orderNumber,
            String customerEmail,
            String customerName,
            String customerPhone,
            NotificationChannel notificationChannel,
            BigDecimal amount,
            String currency,
            String reason
    ) {
        return new PaymentFailedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                orderId,
                orderNumber,
                customerEmail,
                customerName,
                customerPhone,
                notificationChannel,
                amount,
                currency,
                reason
        );
    }
}
