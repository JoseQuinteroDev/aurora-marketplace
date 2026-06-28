package com.aurora.backend.security.token;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Drops rows whose token has already expired. Returns the number deleted. */
    int deleteByExpiresAtBefore(Instant cutoff);

    /**
     * Atomic single-use claim: flip ACTIVE → USED for exactly this row. Returns rows
     * affected (1 = this caller claimed it, 0 = it was already used/revoked). This is
     * the serialization point that guarantees a reset token is honoured at most once.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t "
         + "SET t.status = com.aurora.backend.security.token.PasswordResetTokenStatus.USED, t.usedAt = :now "
         + "WHERE t.id = :id AND t.status = com.aurora.backend.security.token.PasswordResetTokenStatus.ACTIVE")
    int claimForUse(@Param("id") UUID id, @Param("now") Instant now);

    /** Revokes any prior ACTIVE token for the user, so a fresh issue keeps the partial unique index satisfied. */
    @Modifying
    @Query("UPDATE PasswordResetToken t "
         + "SET t.status = com.aurora.backend.security.token.PasswordResetTokenStatus.REVOKED, t.revokedAt = :now "
         + "WHERE t.userId = :userId AND t.status = com.aurora.backend.security.token.PasswordResetTokenStatus.ACTIVE")
    int revokeActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
