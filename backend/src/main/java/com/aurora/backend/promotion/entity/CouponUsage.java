package com.aurora.backend.promotion.entity;

import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupon_usages")
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "used_at", nullable = false, updatable = false)
    private Instant usedAt;

    protected CouponUsage() {
    }

    public CouponUsage(Coupon coupon, User user) {
        this.coupon = coupon;
        this.user = user;
    }

    @PrePersist
    void prePersist() {
        usedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public User getUser() {
        return user;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
