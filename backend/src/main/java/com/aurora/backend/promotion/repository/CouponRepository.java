package com.aurora.backend.promotion.repository;

import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.promotion.entity.Coupon;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}
