package com.aurora.backend.security.token;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, UUID> {

    /**
     * Atomic single-use claim: set {@code consumed_at} for exactly this row iff it is still
     * unconsumed and not expired. Returns rows affected (1 = this caller claimed it, 0 = it was
     * already consumed/expired). This conditional UPDATE is the serialization point that guarantees
     * a challenge is honoured at most once, independent of transaction isolation.
     */
    @Modifying
    @Query("UPDATE MfaChallenge c SET c.consumedAt = :now "
         + "WHERE c.id = :id AND c.consumedAt IS NULL AND c.expiresAt > :now")
    int claimForUse(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Records one wrong-code attempt on an unconsumed challenge: increments {@code attempts} and,
     * once the cap is reached, sets {@code consumed_at} so the challenge can never be retried again
     * (bounding second-factor brute force within the TTL window). Returns rows affected (0 if the
     * challenge was already consumed — nothing to charge).
     */
    @Modifying
    @Query("UPDATE MfaChallenge c "
         + "SET c.attempts = c.attempts + 1, "
         + "c.consumedAt = CASE WHEN c.attempts + 1 >= :maxAttempts THEN :now ELSE c.consumedAt END "
         + "WHERE c.id = :id AND c.consumedAt IS NULL")
    int recordFailedAttempt(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts,
            @Param("now") Instant now);

    /** Drops rows that are expired or already consumed. Returns the number deleted. */
    @Modifying
    @Query("DELETE FROM MfaChallenge c WHERE c.expiresAt < :now OR c.consumedAt IS NOT NULL")
    int deleteExpiredOrConsumed(@Param("now") Instant now);
}
