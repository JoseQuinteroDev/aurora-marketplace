package com.aurora.backend.promotion.repository;

import java.util.UUID;

import com.aurora.backend.promotion.entity.CouponUsage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, UUID> {

    long countByCouponId(UUID couponId);

    long countByCouponIdAndUserId(UUID couponId, UUID userId);
}
