package com.aurora.backend.payment.repository;

import java.util.UUID;

import com.aurora.backend.payment.entity.PaymentAttempt;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
}
