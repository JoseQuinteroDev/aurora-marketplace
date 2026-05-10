package com.aurora.backend.payment.repository;

import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.payment.entity.Payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);
}
