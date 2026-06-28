package com.aurora.backend.security.token;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One opaque refresh token in a rotating family (OWASP A07). Only the SHA-256
 * hash of the secret is stored — the raw {@code rowId.secret} value is shown to
 * the client exactly once. A row is single-use: {@code POST /api/auth/refresh}
 * rotates it to a child in the same {@code familyId}. Replaying an already-rotated
 * token (outside a short grace window) is treated as theft and revokes the family.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "issued_access_jti")
    private UUID issuedAccessJti;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefreshTokenStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {
    }

    public RefreshToken(
            UUID id,
            UUID familyId,
            UUID userId,
            String tokenHash,
            UUID parentId,
            UUID issuedAccessJti,
            Instant expiresAt
    ) {
        this.id = id;
        this.familyId = familyId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.parentId = parentId;
        this.issuedAccessJti = issuedAccessJti;
        this.expiresAt = expiresAt;
        this.status = RefreshTokenStatus.ACTIVE;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isActive(Instant now) {
        return status == RefreshTokenStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getParentId() {
        return parentId;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public UUID getIssuedAccessJti() {
        return issuedAccessJti;
    }

    public RefreshTokenStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
