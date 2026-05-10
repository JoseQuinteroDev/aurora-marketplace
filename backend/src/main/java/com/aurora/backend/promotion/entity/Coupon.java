package com.aurora.backend.promotion.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CouponType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "max_uses_per_user")
    private Integer maxUsesPerUser;

    @Column(name = "minimum_order_amount", precision = 12, scale = 2)
    private BigDecimal minimumOrderAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Coupon() {
    }

    public Coupon(
            String code,
            CouponType type,
            BigDecimal value,
            boolean active,
            Instant startsAt,
            Instant endsAt,
            Integer maxUses,
            Integer maxUsesPerUser,
            BigDecimal minimumOrderAmount
    ) {
        this.code = code;
        this.type = type;
        this.value = value;
        this.active = active;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.maxUses = maxUses;
        this.maxUsesPerUser = maxUsesPerUser;
        this.minimumOrderAmount = minimumOrderAmount;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public CouponType getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public Integer getMaxUsesPerUser() {
        return maxUsesPerUser;
    }

    public BigDecimal getMinimumOrderAmount() {
        return minimumOrderAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(
            String code,
            CouponType type,
            BigDecimal value,
            boolean active,
            Instant startsAt,
            Instant endsAt,
            Integer maxUses,
            Integer maxUsesPerUser,
            BigDecimal minimumOrderAmount
    ) {
        this.code = code;
        this.type = type;
        this.value = value;
        this.active = active;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.maxUses = maxUses;
        this.maxUsesPerUser = maxUsesPerUser;
        this.minimumOrderAmount = minimumOrderAmount;
    }

    public void deactivate() {
        this.active = false;
    }
}
