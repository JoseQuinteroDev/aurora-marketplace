package com.aurora.backend.security.token;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A single-use, short-TTL TOTP login challenge (OWASP A07). Mirrors {@link PasswordResetToken}:
 * only the SHA-256 hash of the opaque secret is stored; the raw {@code rowId.secret} value is
 * returned to the client exactly once as the {@code mfaToken}. It is NOT a session — it cannot be
 * presented as an access or refresh token, lives only in {@code mfa_challenges}, expires in minutes,
 * and is single-use: {@code consumed_at} is set the moment it is exchanged for real tokens (or once
 * the bounded {@code attempts} cap is hit), after which it can never be claimed again.
 */
@Entity
@Table(name = "mfa_challenges")
public class MfaChallenge {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MfaChallenge() {
    }

    public MfaChallenge(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Claimable iff not yet consumed and not expired. */
    public boolean isActive(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
