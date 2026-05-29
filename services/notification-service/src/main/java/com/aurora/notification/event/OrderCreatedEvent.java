package com.aurora.notification.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Local view of the {@code aurora.orders.created} event. Tolerant to unknown
 * fields so the producer can evolve the contract without breaking consumers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(
        String eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        String customerEmail,
        String customerName,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal total,
        String currency,
        String status
) {
}
