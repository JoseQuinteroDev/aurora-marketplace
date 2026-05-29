package com.aurora.backend.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published when a payment for an order is successfully confirmed. */
public record PaymentConfirmedEvent(
        String eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        String customerEmail,
        String customerName,
        BigDecimal amount,
        String currency,
        String paymentMethod
) {

    public static PaymentConfirmedEvent of(
            UUID orderId,
            String orderNumber,
            String customerEmail,
            String customerName,
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
                amount,
                currency,
                paymentMethod
        );
    }
}
