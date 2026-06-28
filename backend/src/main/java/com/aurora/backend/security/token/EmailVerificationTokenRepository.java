package com.aurora.backend.security.token;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    /** Drops rows whose token has already expired. Returns the number deleted. */
    int deleteByExpiresAtBefore(Instant cutoff);

    /**
     * Atomic single-use claim: flip ACTIVE → USED for exactly this row. Returns rows affected
     * (1 = claimed, 0 = already used/revoked) — the serialization point honouring a token once.
     */
    @Modifying
    @Query("UPDATE EmailVerificationToken t "
         + "SET t.status = com.aurora.backend.security.token.EmailVerificationTokenStatus.USED, t.usedAt = :now "
         + "WHERE t.id = :id AND t.status = com.aurora.backend.security.token.EmailVerificationTokenStatus.ACTIVE")
    int claimForUse(@Param("id") UUID id, @Param("now") Instant now);

    /** Revokes any prior ACTIVE token for the user, so a fresh issue keeps the partial unique index satisfied. */
    @Modifying
    @Query("UPDATE EmailVerificationToken t "
         + "SET t.status = com.aurora.backend.security.token.EmailVerificationTokenStatus.REVOKED, t.revokedAt = :now "
         + "WHERE t.userId = :userId AND t.status = com.aurora.backend.security.token.EmailVerificationTokenStatus.ACTIVE")
    int revokeActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
