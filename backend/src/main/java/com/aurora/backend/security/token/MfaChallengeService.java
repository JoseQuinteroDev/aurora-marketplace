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
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-use, short-TTL TOTP login challenges (OWASP A07) — the MFA login-gating increment.
 *
 * <p>Opaque {@code rowId.secret} tokens, SHA-256 at rest, short-lived (default 5 min), with a
 * bounded per-challenge attempt cap (default 5). The challenge is NOT a session: it is never
 * accepted by the JWT filter or the refresh path; it lives only in {@code mfa_challenges} and is
 * the bridge between the password step ({@code AuthService.login} for an MFA-enabled user, which
 * issues NO tokens) and {@code POST /api/auth/mfa/verify} (the only MFA-login token-issuing path).
 *
 * <p>Mirrors {@link PasswordResetTokenService}: consumption is atomic (a conditional UPDATE is the
 * serialization point, so a challenge is honoured at most once), the secret check is constant-time,
 * and every validation failure collapses to one generic {@code 401 MFA_CHALLENGE_INVALID} — the
 * challenge-not-found and wrong-code responses are byte-identical. The raw token/secret is never
 * logged; only the userId is.
 */
@Service
public class MfaChallengeService {

    private static final Logger log = LoggerFactory.getLogger(MfaChallengeService.class);

    private final MfaChallengeRepository repository;
    private final long ttlMinutes;
    private final int maxAttempts;
    private final SecureRandom random = new SecureRandom();

    public MfaChallengeService(
            MfaChallengeRepository repository,
            @Value("${app.security.mfa.challenge.ttl-minutes:5}") long ttlMinutes,
            @Value("${app.security.mfa.challenge.max-attempts:5}") int maxAttempts
    ) {
        this.repository = repository;
        this.ttlMinutes = ttlMinutes;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Mints a fresh challenge bound to the user and returns the raw {@code rowId.secret} — the only
     * place it ever exists; the hash is what is stored. Audited/logged by userId only.
     */
    @Transactional
    public String issue(User user) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        String secret = newSecret();
        repository.save(new MfaChallenge(
                id, user.getId(), hash(secret), now.plus(Duration.ofMinutes(ttlMinutes))));
        log.info("MFA challenge issued (userId={}).", user.getId());
        return id + "." + secret;
    }

    /**
     * Validates a raw challenge token WITHOUT consuming it, returning the bound row id + user id.
     * Throws {@code 401 MFA_CHALLENGE_INVALID} (one generic message) for a not-found / wrong-secret
     * / expired / already-consumed / over-cap challenge. The caller then either {@link #consume}s it
     * (on a correct code) or {@link #recordFailedAttempt}s it (on a wrong code).
     */
    @Transactional(readOnly = true)
    public ValidChallenge validate(String rawToken) {
        Instant now = Instant.now();
        ParsedToken parsed = parse(rawToken);
        MfaChallenge row = repository.findById(parsed.id()).orElseThrow(this::invalid);

        // Constant-time secret check — never String.equals.
        if (!MessageDigest.isEqual(hash(parsed.secret()).getBytes(StandardCharsets.UTF_8),
                row.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw invalid();
        }
        if (!row.isActive(now)) {
            throw invalid(); // expired or already consumed (single-use / over-cap)
        }
        if (row.getAttempts() >= maxAttempts) {
            throw invalid(); // cap already reached
        }
        return new ValidChallenge(row.getId(), row.getUserId());
    }

    /**
     * Atomically single-uses a validated challenge. The conditional UPDATE is the serialization
     * point: returns normally only if THIS caller claimed it; a concurrent double-submit (or any
     * already-consumed/expired race) loses and gets the generic {@code 401}.
     */
    @Transactional
    public void consume(UUID challengeId) {
        if (repository.claimForUse(challengeId, Instant.now()) == 0) {
            throw invalid();
        }
    }

    /**
     * Records a wrong-code attempt: increments the counter and invalidates the challenge once the
     * cap is reached (so it can't be retried). Best-effort accounting — committed in its own
     * transaction by the caller's flow; a no-op if the row was already consumed.
     */
    @Transactional
    public void recordFailedAttempt(UUID challengeId) {
        repository.recordFailedAttempt(challengeId, maxAttempts, Instant.now());
    }

    @Scheduled(fixedDelayString = "${app.security.mfa.challenge.purge-delay-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpiredOrConsumed(Instant.now());
        if (removed > 0) {
            log.debug("Purged {} expired/consumed MFA challenges.", removed);
        }
    }

    /** The row id (for atomic consume) + the user the challenge is bound to. */
    public record ValidChallenge(UUID challengeId, UUID userId) {
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
            throw invalid();
        }
        int dot = rawToken.indexOf('.');
        if (dot <= 0 || dot == rawToken.length() - 1) {
            throw invalid();
        }
        UUID id = parseUuid(rawToken.substring(0, dot));
        if (id == null) {
            throw invalid();
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

    private BusinessException invalid() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "MFA_CHALLENGE_INVALID",
                "Invalid or expired verification challenge.");
    }

    private record ParsedToken(UUID id, String secret) {
    }
}
