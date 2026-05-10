package com.aurora.backend.payment.dto;

import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.payment.entity.PaymentAttempt;
import com.aurora.backend.payment.entity.PaymentStatus;

public record PaymentAttemptResponse(
        UUID id,
        boolean success,
        PaymentStatus status,
        String message,
        Instant createdAt
) {

    public static PaymentAttemptResponse from(PaymentAttempt attempt) {
        return new PaymentAttemptResponse(
                attempt.getId(),
                attempt.isSuccess(),
                attempt.getStatus(),
                attempt.getMessage(),
                attempt.getCreatedAt()
        );
    }
}
