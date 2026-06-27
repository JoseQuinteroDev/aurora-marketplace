package com.aurora.backend.checkout.repository;

import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.checkout.entity.CheckoutIdempotencyKey;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutIdempotencyKeyRepository extends JpaRepository<CheckoutIdempotencyKey, UUID> {

    Optional<CheckoutIdempotencyKey> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
