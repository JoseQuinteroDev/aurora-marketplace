package com.aurora.backend.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.user.entity.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-use password-reset tokens with reuse-safe consumption (OWASP A07).
 *
 * <p>Opaque {@code rowId.secret} tokens, SHA-256 at rest, short-lived; consumption
 * is atomic (a conditional UPDATE is the serialization point, so a token is honoured
 * at most once). Every validation failure collapses to one generic
 * {@code 401 INVALID_RESET_TOKEN} — no enumeration. The raw token, secret and email
 * are never logged.
 */
@Service
public class PasswordResetTokenService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);

    private final PasswordResetTokenRepository repository;
    private final long expirationMinutes;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetTokenService(
            PasswordResetTokenRepository repository,
            @Value("${app.security.password-reset-token.expiration-minutes:30}") long expirationMinutes
    ) {
        this.repository = repository;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * Mints a single ACTIVE token (revoking any prior ACTIVE one first to satisfy the
     * partial unique index). Returns the raw {@code rowId.secret} — the only place it
     * ever exists in the core; the hash is what is stored.
     *
     * <p>Runs in its OWN transaction (REQUIRES_NEW): if two concurrent requests for the
     * same user collide on the partial unique index, the violation rolls back only this
     * inner tx and surfaces a {@code DataIntegrityViolationException} the caller can catch
     * cleanly — without poisoning the caller's transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issue(User user) {
        Instant now = Instant.now();
        repository.revokeActiveForUser(user.getId(), now);
        UUID id = UUID.randomUUID();
        String secret = newSecret();
        repository.save(new PasswordResetToken(
                id, user.getId(), hash(secret), now.plus(Duration.ofMinutes(expirationMinutes))));
        return id + "." + secret;
    }

    /**
     * Validates and atomically claims a token, returning the authorized user id. Throws
     * {@code 401 INVALID_RESET_TOKEN} (one generic message) for any failure.
     */
    @Transactional
    public UUID consume(String rawToken) {
        Instant now = Instant.now();
        ParsedToken parsed = parse(rawToken);
        PasswordResetToken row = repository.findById(parsed.id()).orElseThrow(this::invalidToken);

        // Constant-time secret check — never String.equals.
        if (!MessageDigest.isEqual(hash(parsed.secret()).getBytes(StandardCharsets.UTF_8),
                row.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw invalidToken();
        }
        if (!row.isActive(now)) {
            throw invalidToken();
        }
        if (repository.claimForUse(row.getId(), now) == 0) {
            throw invalidToken(); // replay / already used (serialization point)
        }
        return row.getUserId();
    }

    /**
     * Pays the hashing cost of {@link #issue} (a SHA-256 of a fresh secret) on the
     * no-account / disabled / throttled branch. This equalizes the CPU hash cost only —
     * it does NOT mask {@code issue}'s DB writes, so the caller additionally applies a
     * fixed response-latency floor to close the user-enumeration timing oracle.
     */
    public void burnEquivalentWork() {
        hash(newSecret());
    }

    @Scheduled(fixedDelayString = "${app.security.password-reset-token.purge-delay-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteByExpiresAtBefore(Instant.now());
        if (removed > 0) {
            log.debug("Purged {} expired password-reset tokens.", removed);
        }
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private ParsedToken parse(String rawToken) {
        if (rawToken == null) {
            throw invalidToken();
        }
        int dot = rawToken.indexOf('.');
        if (dot <= 0 || dot == rawToken.length() - 1) {
            throw invalidToken();
        }
        UUID id = parseUuid(rawToken.substring(0, dot));
        if (id == null) {
            throw invalidToken();
        }
        return new ParsedToken(id, rawToken.substring(dot + 1));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private BusinessException invalidToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_RESET_TOKEN",
                "Invalid or expired reset token.");
    }

    private record ParsedToken(UUID id, String secret) {
    }
}
