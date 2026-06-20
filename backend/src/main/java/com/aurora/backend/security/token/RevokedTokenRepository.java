package com.aurora.backend.security.token;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {

    /** Removes revocations whose tokens have already expired. Returns the number deleted. */
    int deleteByExpiresAtBefore(Instant cutoff);
}
