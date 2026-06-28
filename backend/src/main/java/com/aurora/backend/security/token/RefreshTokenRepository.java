package com.aurora.backend.security.token;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** All rows in a family — used to denylist outstanding access tokens on reuse detection. */
    List<RefreshToken> findByFamilyId(UUID familyId);

    /** Distinct family ids the user owns — used to kill every session on password reset. */
    @Query("SELECT DISTINCT t.familyId FROM RefreshToken t WHERE t.userId = :userId")
    List<UUID> findDistinctFamilyIdByUserId(@Param("userId") UUID userId);

    /**
     * Atomic single-use claim: flip ACTIVE → ROTATED for exactly this row. Returns rows
     * affected (1 = this caller won the rotation race, 0 = it was already consumed). This
     * conditional UPDATE is the serialization point that guarantees one child per parent,
     * independent of transaction isolation.
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.status = com.aurora.backend.security.token.RefreshTokenStatus.ROTATED, "
         + "t.rotatedAt = :now, t.replacedById = :childId "
         + "WHERE t.id = :id AND t.status = com.aurora.backend.security.token.RefreshTokenStatus.ACTIVE")
    int claimForRotation(@Param("id") UUID id, @Param("childId") UUID childId, @Param("now") Instant now);

    /** Revokes every non-revoked row in a family (logout, or reuse detection). */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.status = com.aurora.backend.security.token.RefreshTokenStatus.REVOKED, "
         + "t.revokedAt = :now "
         + "WHERE t.familyId = :familyId AND t.status <> com.aurora.backend.security.token.RefreshTokenStatus.REVOKED")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    /** Drops rows whose refresh token has already expired. Returns the number deleted. */
    int deleteByExpiresAtBefore(Instant cutoff);
}
