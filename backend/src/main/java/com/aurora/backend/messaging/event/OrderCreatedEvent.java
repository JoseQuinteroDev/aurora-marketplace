package com.aurora.backend.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.user.notification.NotificationChannel;

/**
 * Published when a customer confirms checkout and an order is created.
 *
 * <p>Carries enough context for downstream consumers (notifications, analytics,
 * fulfilment) to act without calling back into the core service — including the
 * customer's optional phone and their resolved {@link NotificationChannel}, so
 * the notification-service can deliver the confirmation on the chosen channel.</p>
 */
public record OrderCreatedEvent(
        String eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        String customerEmail,
        String customerName,
        String customerPhone,
        NotificationChannel notificationChannel,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal total,
        String currency,
        String status
) {

    public static OrderCreatedEvent of(
            UUID orderId,
            String orderNumber,
            String customerEmail,
            String customerName,
            String customerPhone,
            NotificationChannel notificationChannel,
            int itemCount,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal total,
            String currency,
            String status
    ) {
        return new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                orderId,
                orderNumber,
                customerEmail,
                customerName,
                customerPhone,
                notificationChannel,
                itemCount,
                subtotal,
                discountTotal,
                total,
                currency,
                status
        );
    }
}
