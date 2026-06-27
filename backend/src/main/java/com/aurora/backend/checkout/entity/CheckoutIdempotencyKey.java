package com.aurora.backend.checkout.entity;

import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.order.entity.Order;
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
import jakarta.persistence.UniqueConstraint;

/**
 * Records that a checkout with a given client-supplied {@code Idempotency-Key} produced a
 * specific order (OWASP A04 — insecure design / safe retries). The {@code (user_id,
 * idempotency_key)} unique constraint guarantees a double-submitted or retried checkout
 * resolves to a single order: the second attempt either replays the stored order or, if it
 * races the first, fails the constraint and rolls back (no duplicate order, no double-charge).
 *
 * <p>The row is only written WITH its order (end-of-transaction), so a committed row always
 * points at a real order — a concurrent reader never sees an in-progress null.
 */
@Entity
@Table(
        name = "checkout_idempotency_keys",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_checkout_idempotency_user_key",
                columnNames = {"user_id", "idempotency_key"}))
public class CheckoutIdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CheckoutIdempotencyKey() {
    }

    public CheckoutIdempotencyKey(User user, String idempotencyKey, Order order) {
        this.user = user;
        this.idempotencyKey = idempotencyKey;
        this.order = order;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Order getOrder() {
        return order;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
