package com.aurora.backend.promotion.repository;

import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.promotion.entity.Coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    /**
     * Loads a coupon under a PESSIMISTIC_WRITE row lock so concurrent redemptions of the
     * same coupon are serialized (OWASP A04). Held until the surrounding transaction commits,
     * which is why redemption must re-check the usage limits AFTER acquiring this lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") UUID id);
}
