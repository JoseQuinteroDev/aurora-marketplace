package com.aurora.backend.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token rotation with automatic reuse detection (OWASP A07).
 *
 * <p>Tokens are opaque, full-entropy, single-use and stored only as a SHA-256 hash.
 * Each {@code POST /api/auth/refresh} rotates the presented token to a child in the
 * same family. Replaying an already-rotated token outside a short grace window is
 * treated as theft: the whole family is revoked and its outstanding access tokens
 * are denylisted. A near-simultaneous double-submit within the grace window is
 * idempotent (an in-memory cache returns the same freshly-minted tokens), so a
 * legitimate user is never logged out by a race. The single-use guarantee itself
 * does not depend on that cache — it rests on the conditional {@code claimForRotation}
 * UPDATE plus the {@code uk_refresh_tokens_active_family} partial unique index.
 *
 * <p>Security-logging rule: never log the raw token or secret — only familyId,
 * userId and exception class names.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int GRACE_CACHE_CAP = 10_000;

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final TokenDenylistService tokenDenylistService;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    private final long expirationDays;
    private final long graceSeconds;

    private final SecureRandom random = new SecureRandom();
    // Benign double-submit cache, keyed by the parent rowId, holding the result it
    // rotated into for `graceSeconds`. Single-instance, best-effort; a miss degrades
    // to "re-login", never to a forked family.
    private final Map<UUID, CachedRotation> graceCache = new ConcurrentHashMap<>();

    public RefreshTokenService(
            RefreshTokenRepository repository,
            UserRepository userRepository,
            TokenDenylistService tokenDenylistService,
            JwtService jwtService,
            AuditLogService auditLogService,
            @Value("${app.security.refresh-token.expiration-days:30}") long expirationDays,
            @Value("${app.security.refresh-token.grace-seconds:10}") long graceSeconds
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.tokenDenylistService = tokenDenylistService;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.expirationDays = expirationDays;
        this.graceSeconds = graceSeconds;
    }

    private record CachedRotation(UUID userId, String accessToken, String rawRefreshToken, Instant cachedAt) {
    }

    /** The freshly-minted access token + rotated refresh token + the resolved user. */
    public record RotationResult(User user, String accessToken, String rawRefreshToken) {
    }

    /** Issues the first refresh token of a NEW family, linked to the access token's jti. */
    @Transactional
    public String issue(User user, String accessJti) {
        UUID id = UUID.randomUUID();
        String secret = newSecret();
        Instant now = Instant.now();
        RefreshToken row = new RefreshToken(
                id,
                UUID.randomUUID(),
                user.getId(),
                hash(secret),
                null,
                parseUuid(accessJti),
                now.plus(Duration.ofDays(expirationDays))
        );
        repository.save(row);
        return id + "." + secret;
    }

    /**
     * Validates, single-use-rotates, and reuse-detects a refresh token. Throws
     * {@code 401 INVALID_REFRESH_TOKEN} (one generic message — anti-enumeration) for a
     * not-found / wrong-secret / expired / revoked / replayed token.
     */
    @Transactional
    public RotationResult rotate(String rawRefreshToken) {
        Instant now = Instant.now();
        ParsedToken parsed = parse(rawRefreshToken);

        RefreshToken row = repository.findById(parsed.id()).orElseThrow(this::invalidToken);

        // Constant-time secret check over equal-length hex; mismatch is indistinguishable
        // from a guessed rowId — same generic failure either way.
        if (!MessageDigest.isEqual(hash(parsed.secret()).getBytes(StandardCharsets.UTF_8),
                row.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw invalidToken();
        }
        if (!row.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }
        if (row.getStatus() == RefreshTokenStatus.REVOKED) {
            throw invalidToken();
        }
        if (row.getStatus() == RefreshTokenStatus.ROTATED) {
            return handleRotatedReplay(row, now);
        }
        return rotateActive(row, now);
    }

    /** Best-effort family revoke for logout. Never throws on a junk/unknown token. */
    @Transactional
    public void revokeFamilyOf(String rawRefreshToken) {
        try {
            ParsedToken parsed = parse(rawRefreshToken);
            repository.findById(parsed.id())
                    .ifPresent(row -> repository.revokeFamily(row.getFamilyId(), Instant.now()));
        } catch (RuntimeException exception) {
            log.debug("Logout: refresh token not usable for family revoke ({}).",
                    exception.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${app.security.refresh-token.purge-delay-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteByExpiresAtBefore(Instant.now());
        if (removed > 0) {
            log.debug("Purged {} expired refresh tokens.", removed);
        }
    }

    private RotationResult rotateActive(RefreshToken row, Instant now) {
        User user = userRepository.findById(row.getUserId()).orElseThrow(this::invalidToken);

        String accessToken = jwtService.generateToken(user);
        UUID newJti = parseUuid(jwtService.extractJti(accessToken));
        UUID childId = UUID.randomUUID();
        String childSecret = newSecret();

        // The conditional UPDATE is the serialization point: 1 = we rotated it, 0 = a
        // concurrent caller already did (return the cached benign result, never mint twice).
        if (repository.claimForRotation(row.getId(), childId, now) == 0) {
            CachedRotation cached = validCache(row.getId(), now);
            if (cached != null) {
                return toResult(cached);
            }
            throw invalidToken();
        }

        RefreshToken child = new RefreshToken(
                childId,
                row.getFamilyId(),
                row.getUserId(),
                hash(childSecret),
                row.getId(),
                newJti,
                now.plus(Duration.ofDays(expirationDays))
        );
        repository.save(child);

        String rawChild = childId + "." + childSecret;
        cacheRotation(row.getId(), new CachedRotation(user.getId(), accessToken, rawChild, now));
        return new RotationResult(user, accessToken, rawChild);
    }

    private RotationResult handleRotatedReplay(RefreshToken row, Instant now) {
        CachedRotation cached = validCache(row.getId(), now);
        if (cached != null) {
            return toResult(cached); // benign double-submit within the grace window
        }
        detectReuse(row, now);
        throw invalidToken();
    }

    private void detectReuse(RefreshToken row, Instant now) {
        repository.revokeFamily(row.getFamilyId(), now);

        // Denylist the family's still-living access tokens (15-min TTL means most are
        // already expired; this closes the window where one is still valid).
        Duration accessTtl = Duration.ofMinutes(jwtService.getExpirationMinutes());
        for (RefreshToken familyRow : repository.findByFamilyId(row.getFamilyId())) {
            if (familyRow.getIssuedAccessJti() != null && familyRow.getCreatedAt() != null) {
                tokenDenylistService.revoke(
                        familyRow.getIssuedAccessJti().toString(),
                        familyRow.getCreatedAt().plus(accessTtl));
            }
        }

        userRepository.findById(row.getUserId()).ifPresent(user -> auditLogService.log(
                AuditEventType.REFRESH_TOKEN_REUSED, user, "REFRESH_TOKEN", row.getFamilyId(),
                "Refresh-token reuse detected; token family revoked."));
        log.warn("Refresh-token reuse detected; family {} revoked.", row.getFamilyId());
    }

    private RotationResult toResult(CachedRotation cached) {
        User user = userRepository.findById(cached.userId()).orElseThrow(this::invalidToken);
        return new RotationResult(user, cached.accessToken(), cached.rawRefreshToken());
    }

    private CachedRotation validCache(UUID rowId, Instant now) {
        CachedRotation cached = graceCache.get(rowId);
        if (cached == null) {
            return null;
        }
        if (cached.cachedAt().isBefore(now.minusSeconds(graceSeconds))) {
            graceCache.remove(rowId);
            return null;
        }
        return cached;
    }

    private void cacheRotation(UUID rowId, CachedRotation entry) {
        if (graceCache.size() > GRACE_CACHE_CAP) {
            Instant cutoff = entry.cachedAt().minusSeconds(graceSeconds);
            graceCache.entrySet().removeIf(e -> e.getValue().cachedAt().isBefore(cutoff));
        }
        graceCache.put(rowId, entry);
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

    private ParsedToken parse(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            throw invalidToken();
        }
        int dot = rawRefreshToken.indexOf('.');
        if (dot <= 0 || dot == rawRefreshToken.length() - 1) {
            throw invalidToken();
        }
        UUID id = parseUuid(rawRefreshToken.substring(0, dot));
        if (id == null) {
            throw invalidToken();
        }
        return new ParsedToken(id, rawRefreshToken.substring(dot + 1));
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
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "Invalid or expired refresh token.");
    }

    private record ParsedToken(UUID id, String secret) {
    }
}
