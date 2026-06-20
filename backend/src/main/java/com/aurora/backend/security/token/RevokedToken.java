package com.aurora.backend.security.token;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A revoked access token, keyed by its {@code jti} (JWT id). Present rows mean
 * "reject this token even though its signature and expiry still check out."
 * Backs server-side logout / revocation (OWASP A07).
 */
@Entity
@Table(name = "token_denylist")
public class RevokedToken {

    @Id
    @Column(name = "jti")
    private UUID jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false, updatable = false)
    private Instant revokedAt;

    protected RevokedToken() {
    }

    public RevokedToken(UUID jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void prePersist() {
        revokedAt = Instant.now();
    }

    public UUID getJti() {
        return jti;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
