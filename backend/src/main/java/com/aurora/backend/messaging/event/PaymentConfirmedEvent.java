package com.aurora.backend.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.user.notification.NotificationChannel;

/** Published when a payment for an order is successfully confirmed. */
public record PaymentConfirmedEvent(
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
        String paymentMethod
) {

    public static PaymentConfirmedEvent of(
            UUID orderId,
            String orderNumber,
            String customerEmail,
            String customerName,
            String customerPhone,
            NotificationChannel notificationChannel,
            BigDecimal amount,
            String currency,
            String paymentMethod
    ) {
        return new PaymentConfirmedEvent(
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
                paymentMethod
        );
    }
}
