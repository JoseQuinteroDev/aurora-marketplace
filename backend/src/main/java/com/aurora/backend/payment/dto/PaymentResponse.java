package com.aurora.backend.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aurora.backend.order.entity.OrderStatus;
import com.aurora.backend.payment.entity.Payment;
import com.aurora.backend.payment.entity.PaymentMethod;
import com.aurora.backend.payment.entity.PaymentStatus;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        PaymentMethod method,
        BigDecimal amount,
        List<PaymentAttemptResponse> attempts,
        Instant createdAt,
        Instant updatedAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getOrder().getOrderNumber(),
                payment.getOrder().getStatus(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getAttempts().stream().map(PaymentAttemptResponse::from).toList(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
