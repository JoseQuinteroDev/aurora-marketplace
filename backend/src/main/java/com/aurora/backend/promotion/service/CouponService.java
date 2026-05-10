package com.aurora.backend.promotion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.promotion.dto.CouponRequest;
import com.aurora.backend.promotion.dto.CouponResponse;
import com.aurora.backend.promotion.entity.Coupon;
import com.aurora.backend.promotion.entity.CouponType;
import com.aurora.backend.promotion.repository.CouponRepository;
import com.aurora.backend.promotion.repository.CouponUsageRepository;
import com.aurora.backend.user.entity.User;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    public CouponService(CouponRepository couponRepository, CouponUsageRepository couponUsageRepository) {
        this.couponRepository = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> listCoupons() {
        return couponRepository.findAll().stream()
                .map(CouponResponse::from)
                .toList();
    }

    @Transactional
    public CouponResponse createCoupon(CouponRequest request) {
        validateCouponRequest(request);
        String code = normalizeCode(request.code());

        if (couponRepository.existsByCode(code)) {
            throw duplicateCodeException();
        }

        Coupon coupon = new Coupon(
                code,
                request.type(),
                money(request.value()),
                activeOrDefault(request.active()),
                request.startsAt(),
                request.endsAt(),
                request.maxUses(),
                request.maxUsesPerUser(),
                moneyOrNull(request.minimumOrderAmount())
        );

        return CouponResponse.from(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResponse updateCoupon(UUID id, CouponRequest request) {
        validateCouponRequest(request);
        Coupon coupon = getCoupon(id);
        String code = normalizeCode(request.code());

        if (couponRepository.existsByCodeAndIdNot(code, id)) {
            throw duplicateCodeException();
        }

        coupon.update(
                code,
                request.type(),
                money(request.value()),
                activeOrDefault(request.active()),
                request.startsAt(),
                request.endsAt(),
                request.maxUses(),
                request.maxUsesPerUser(),
                moneyOrNull(request.minimumOrderAmount())
        );

        return CouponResponse.from(coupon);
    }

    @Transactional
    public void deleteCoupon(UUID id) {
        getCoupon(id).deactivate();
    }

    @Transactional(readOnly = true)
    public Coupon validateCouponForCart(String rawCode, User user, BigDecimal subtotal) {
        Coupon coupon = couponRepository.findByCode(normalizeCode(rawCode))
                .orElseThrow(() -> new NotFoundException("Coupon", rawCode));

        validateCouponCanApply(coupon, user, subtotal);
        return coupon;
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateDiscount(Coupon coupon, User user, BigDecimal subtotal) {
        if (coupon == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        try {
            validateCouponCanApply(coupon, user, subtotal);
        } catch (BusinessException exception) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal discount = switch (coupon.getType()) {
            case PERCENTAGE -> subtotal.multiply(coupon.getValue()).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> coupon.getValue();
        };

        if (discount.compareTo(subtotal) > 0) {
            return subtotal.setScale(2, RoundingMode.HALF_UP);
        }

        return money(discount);
    }

    private Coupon getCoupon(UUID id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Coupon", id));
    }

    private void validateCouponCanApply(Coupon coupon, User user, BigDecimal subtotal) {
        Instant now = Instant.now();

        if (!coupon.isActive()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COUPON_INACTIVE", "Coupon is not active.");
        }

        if (coupon.getStartsAt() != null && coupon.getStartsAt().isAfter(now)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COUPON_NOT_STARTED", "Coupon is not active yet.");
        }

        if (coupon.getEndsAt() != null && coupon.getEndsAt().isBefore(now)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "Coupon has expired.");
        }

        if (coupon.getMinimumOrderAmount() != null && subtotal.compareTo(coupon.getMinimumOrderAmount()) < 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "COUPON_MINIMUM_ORDER_NOT_MET",
                    "Cart subtotal does not meet the coupon minimum order amount."
            );
        }

        if (coupon.getMaxUses() != null && couponUsageRepository.countByCouponId(coupon.getId()) >= coupon.getMaxUses()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COUPON_MAX_USES_REACHED", "Coupon usage limit reached.");
        }

        if (coupon.getMaxUsesPerUser() != null
                && couponUsageRepository.countByCouponIdAndUserId(coupon.getId(), user.getId()) >= coupon.getMaxUsesPerUser()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "COUPON_USER_MAX_USES_REACHED",
                    "Coupon usage limit reached for this user."
            );
        }
    }

    private void validateCouponRequest(CouponRequest request) {
        if (request.startsAt() != null && request.endsAt() != null && !request.startsAt().isBefore(request.endsAt())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_COUPON_DATE_RANGE",
                    "Coupon start date must be before end date."
            );
        }

        if (request.type() == CouponType.PERCENTAGE && request.value().compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_COUPON_PERCENTAGE",
                    "Percentage coupon value cannot be greater than 100."
            );
        }
    }

    private BusinessException duplicateCodeException() {
        return new BusinessException(HttpStatus.CONFLICT, "COUPON_CODE_EXISTS", "Coupon code already exists.");
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal moneyOrNull(BigDecimal value) {
        return value == null ? null : money(value);
    }

    private boolean activeOrDefault(Boolean active) {
        return active == null || active;
    }
}
