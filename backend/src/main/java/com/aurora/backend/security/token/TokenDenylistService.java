package com.aurora.backend.security.token;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side JWT revocation (OWASP A07). Stateless tokens are otherwise valid
 * until natural expiry; this lets logout (and future password-change /
 * account-disable flows) invalidate a specific token by its {@code jti} before
 * it expires. The auth filter checks every authenticated request against this
 * denylist, so the store is kept small by holding only still-unexpired
 * revocations and purging the rest on a schedule.
 */
@Service
public class TokenDenylistService {

    private static final Logger log = LoggerFactory.getLogger(TokenDenylistService.class);

    private final RevokedTokenRepository repository;

    public TokenDenylistService(RevokedTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void revoke(String jti, Instant expiresAt) {
        UUID id = parse(jti);
        if (id == null || expiresAt == null || repository.existsById(id)) {
            return;
        }
        repository.save(new RevokedToken(id, expiresAt));
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        UUID id = parse(jti);
        return id != null && repository.existsById(id);
    }

    /** Drops revocations whose tokens have already expired (those are rejected on expiry anyway). */
    @Scheduled(fixedDelayString = "${app.security.token-denylist.purge-delay-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteByExpiresAtBefore(Instant.now());
        if (removed > 0) {
            log.debug("Purged {} expired token-denylist entries.", removed);
        }
    }

    private UUID parse(String jti) {
        if (jti == null || jti.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(jti);
        } catch (IllegalArgumentException exception) {
            // Non-UUID jti (e.g. a forged token) — the signature check already rejects these.
            return null;
        }
    }
}
