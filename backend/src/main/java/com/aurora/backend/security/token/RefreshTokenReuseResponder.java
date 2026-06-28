package com.aurora.backend.security.token;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits the family-wide responses to refresh-token events (OWASP A07) in their
 * OWN transactions, so they survive even when the caller's transaction rolls back.
 *
 * <p>This is load-bearing for reuse detection: {@code RefreshTokenService.rotate()}
 * signals theft by throwing a {@code 401} ({@code BusinessException}), which would
 * otherwise roll back the very revoke + denylist + audit that the theft response
 * performs — silently undoing it. Running that response in a {@code REQUIRES_NEW}
 * transaction on a <b>separate bean</b> (so the call goes through the Spring proxy,
 * not a self-invocation) makes it commit before the caller unwinds.
 */
@Component
public class RefreshTokenReuseResponder {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenReuseResponder.class);

    private final RefreshTokenRepository repository;
    private final TokenDenylistService tokenDenylistService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public RefreshTokenReuseResponder(
            RefreshTokenRepository repository,
            TokenDenylistService tokenDenylistService,
            JwtService jwtService,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.tokenDenylistService = tokenDenylistService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Theft response — committed in a NEW transaction, independent of the caller's
     * (which throws a 401 and rolls back). Revokes the family, denylists its
     * outstanding access tokens, and audits the event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void respondToReuse(UUID familyId, UUID userId) {
        repository.revokeFamily(familyId, Instant.now());
        denylistFamilyAccessTokens(familyId);
        userRepository.findById(userId).ifPresent(user -> auditLogService.log(
                AuditEventType.REFRESH_TOKEN_REUSED, user, "REFRESH_TOKEN", familyId,
                "Refresh-token reuse detected; token family revoked."));
        log.warn("Refresh-token reuse detected; family {} revoked.", familyId);
    }

    /**
     * Logout / explicit revoke: revoke the family AND denylist its still-living
     * access tokens (symmetry with reuse detection — a status flip alone would
     * leave a prior-hop access token valid for up to the access-token TTL).
     */
    @Transactional
    public void revokeFamily(UUID familyId) {
        repository.revokeFamily(familyId, Instant.now());
        denylistFamilyAccessTokens(familyId);
    }

    private void denylistFamilyAccessTokens(UUID familyId) {
        Duration accessTtl = Duration.ofMinutes(jwtService.getExpirationMinutes());
        repository.findByFamilyId(familyId).forEach(familyRow -> {
            if (familyRow.getIssuedAccessJti() != null && familyRow.getCreatedAt() != null) {
                tokenDenylistService.revoke(
                        familyRow.getIssuedAccessJti().toString(),
                        familyRow.getCreatedAt().plus(accessTtl));
            }
        });
    }
}
