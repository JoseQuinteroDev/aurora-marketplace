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
 * Single-use email-verification tokens (OWASP A07). Mirrors {@link PasswordResetTokenService}:
 * opaque {@code rowId.secret} tokens, SHA-256 at rest, short-lived; consumption is atomic.
 * Every validation failure collapses to a generic {@code 401 INVALID_VERIFICATION_TOKEN}.
 *
 * <p>Two issue variants exist on purpose (FK-visibility): {@link #issueInCurrentTransaction}
 * joins the caller's transaction — required by {@code register()}, where the new user row is
 * not yet committed, so a {@code REQUIRES_NEW} insert on a separate connection would violate
 * the user FK. {@link #issue} uses {@code REQUIRES_NEW} for resend (no outer transaction).
 */
@Service
public class EmailVerificationTokenService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationTokenService.class);

    private final EmailVerificationTokenRepository repository;
    private final long expirationMinutes;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationTokenService(
            EmailVerificationTokenRepository repository,
            @Value("${app.security.email-verification-token.expiration-minutes:1440}") long expirationMinutes
    ) {
        this.repository = repository;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * Mints a token in the CALLER's transaction (used by registration). The FK to the
     * just-saved-but-uncommitted user row resolves on the same connection. A brand-new user
     * has no prior ACTIVE token, so no revoke is needed.
     */
    @Transactional
    public String issueInCurrentTransaction(User user) {
        return persist(user);
    }

    /**
     * Mints a token in its OWN transaction (used by resend, which has no outer transaction and
     * whose user was committed long ago). REQUIRES_NEW so a partial-unique-index race rolls back
     * only this inner tx and is catchable by the caller as a clean success.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issue(User user) {
        repository.revokeActiveForUser(user.getId(), Instant.now());
        return persist(user);
    }

    private String persist(User user) {
        UUID id = UUID.randomUUID();
        String secret = newSecret();
        repository.save(new EmailVerificationToken(
                id, user.getId(), hash(secret), Instant.now().plus(Duration.ofMinutes(expirationMinutes))));
        return id + "." + secret;
    }

    /**
     * Validates and atomically claims a token, returning the authorized user id. Throws
     * {@code 401 INVALID_VERIFICATION_TOKEN} (one generic message) for any failure.
     */
    @Transactional
    public UUID consume(String rawToken) {
        Instant now = Instant.now();
        ParsedToken parsed = parse(rawToken);
        EmailVerificationToken row = repository.findById(parsed.id()).orElseThrow(this::invalidToken);

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

    /** Pays the hash cost of {@link #issue} without DB writes (timing parity on the resend no-op branch). */
    public void burnEquivalentWork() {
        hash(newSecret());
    }

    @Scheduled(fixedDelayString = "${app.security.email-verification-token.purge-delay-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteByExpiresAtBefore(Instant.now());
        if (removed > 0) {
            log.debug("Purged {} expired email-verification tokens.", removed);
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
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_VERIFICATION_TOKEN",
                "Invalid or expired verification link.");
    }

    private record ParsedToken(UUID id, String secret) {
    }
}
