package com.aurora.backend.promotion.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.promotion.entity.Coupon;
import com.aurora.backend.promotion.entity.CouponType;
import com.aurora.backend.promotion.entity.CouponUsage;
import com.aurora.backend.promotion.repository.CouponRepository;
import com.aurora.backend.promotion.repository.CouponUsageRepository;
import com.aurora.backend.user.entity.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CouponService} discount + eligibility logic (OWASP A04 —
 * business logic). These lock the "coupons cannot be over-applied or reused
 * beyond their limit" rule from the manual pentest checklist, and the
 * server-side discount math that checkout relies on.
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final BigDecimal SUBTOTAL = new BigDecimal("250.00");

    @Mock private CouponRepository couponRepository;
    @Mock private CouponUsageRepository couponUsageRepository;

    @InjectMocks
    private CouponService couponService;

    private Coupon coupon(CouponType type, String value, boolean active) {
        return new Coupon("SAVE", type, new BigDecimal(value), active, null, null, null, null, null);
    }

    @Test
    void noCouponMeansNoDiscount() {
        assertThat(couponService.calculateDiscount(null, mock(User.class), SUBTOTAL))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void percentageDiscountIsComputedAndRounded() {
        Coupon ten = coupon(CouponType.PERCENTAGE, "10.00", true);

        BigDecimal discount = couponService.calculateDiscount(ten, mock(User.class), SUBTOTAL);

        assertThat(discount).isEqualByComparingTo("25.00");   // 10% of 250.00
    }

    @Test
    void fixedAmountDiscountIsApplied() {
        Coupon thirty = coupon(CouponType.FIXED_AMOUNT, "30.00", true);

        assertThat(couponService.calculateDiscount(thirty, mock(User.class), SUBTOTAL))
                .isEqualByComparingTo("30.00");
    }

    @Test
    void discountIsNeverGreaterThanTheSubtotal() {
        // A 999 fixed coupon on a 250 cart cannot create a negative-priced order.
        Coupon huge = coupon(CouponType.FIXED_AMOUNT, "999.00", true);

        assertThat(couponService.calculateDiscount(huge, mock(User.class), SUBTOTAL))
                .isEqualByComparingTo("250.00");
    }

    @Test
    void inactiveCouponContributesNoDiscount() {
        Coupon inactive = coupon(CouponType.PERCENTAGE, "50.00", false);

        assertThat(couponService.calculateDiscount(inactive, mock(User.class), SUBTOTAL))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void expiredCouponContributesNoDiscount() {
        Coupon expired = new Coupon(
                "OLD", CouponType.PERCENTAGE, new BigDecimal("50.00"), true,
                null, Instant.now().minus(1, ChronoUnit.DAYS), null, null, null);

        assertThat(couponService.calculateDiscount(expired, mock(User.class), SUBTOTAL))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void couponPastItsGlobalUseLimitContributesNoDiscount() {
        Coupon capped = new Coupon(
                "LIMITED", CouponType.FIXED_AMOUNT, new BigDecimal("30.00"), true,
                null, null, 5, null, null);
        when(couponUsageRepository.countByCouponId(any())).thenReturn(5L);   // limit reached

        assertThat(couponService.calculateDiscount(capped, mock(User.class), SUBTOTAL))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void validateCouponForCartRejectsAnUnknownCode() {
        when(couponRepository.findByCode("SAVE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.validateCouponForCart("save", mock(User.class), SUBTOTAL))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void validateCouponForCartRejectsReuseBeyondPerUserLimit() {
        Coupon perUser = new Coupon(
                "ONCE", CouponType.FIXED_AMOUNT, new BigDecimal("30.00"), true,
                null, null, null, 1, null);
        when(couponRepository.findByCode("ONCE")).thenReturn(Optional.of(perUser));
        when(couponUsageRepository.countByCouponIdAndUserId(any(), any())).thenReturn(1L);

        assertThatThrownBy(() -> couponService.validateCouponForCart("ONCE", mock(User.class), SUBTOTAL))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("COUPON_USER_MAX_USES_REACHED"));
    }

    // --- atomic redemption (OWASP A04 — concurrency integrity) ---

    @Test
    void redeemTakesTheRowLockReChecksAndRecordsUsageWhenUnderTheLimit() {
        Coupon capped = new Coupon(
                "SAVE", CouponType.FIXED_AMOUNT, new BigDecimal("30.00"), true, null, null, 5, null, null);
        when(couponRepository.findByIdForUpdate(any())).thenReturn(Optional.of(capped));
        when(couponUsageRepository.countByCouponId(any())).thenReturn(2L);   // under the limit of 5

        couponService.redeem(capped, mock(User.class));

        // It re-reads under the lock and records exactly one usage.
        verify(couponRepository).findByIdForUpdate(any());
        verify(couponUsageRepository).save(any(CouponUsage.class));
    }

    @Test
    void redeemRejectsAtTheGlobalLimitUnderTheLockWithoutRecording() {
        Coupon capped = new Coupon(
                "LIMITED", CouponType.FIXED_AMOUNT, new BigDecimal("30.00"), true, null, null, 1, null, null);
        when(couponRepository.findByIdForUpdate(any())).thenReturn(Optional.of(capped));
        when(couponUsageRepository.countByCouponId(any())).thenReturn(1L);   // limit already reached

        assertThatThrownBy(() -> couponService.redeem(capped, mock(User.class)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("COUPON_MAX_USES_REACHED"));

        verify(couponUsageRepository, never()).save(any());
    }
}
