package com.aurora.backend.auth.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.auth.dto.AuthResponse;
import com.aurora.backend.auth.dto.AuthUserResponse;
import com.aurora.backend.auth.dto.ForgotPasswordRequest;
import com.aurora.backend.auth.dto.LoginRequest;
import com.aurora.backend.auth.dto.RefreshRequest;
import com.aurora.backend.auth.dto.RegisterRequest;
import com.aurora.backend.auth.dto.ResetPasswordRequest;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.messaging.AuroraTopics;
import com.aurora.backend.messaging.event.PasswordResetRequestedEvent;
import com.aurora.backend.messaging.outbox.OutboxEventRecorder;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.security.token.PasswordResetTokenService;
import com.aurora.backend.security.token.RefreshTokenRepository;
import com.aurora.backend.security.token.RefreshTokenReuseResponder;
import com.aurora.backend.security.token.RefreshTokenService;
import com.aurora.backend.security.token.TokenDenylistService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;
import com.aurora.backend.user.role.Role;
import io.jsonwebtoken.JwtException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final TokenDenylistService tokenDenylistService;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final OutboxEventRecorder outboxRecorder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenReuseResponder reuseResponder;
    private final int resetExpiryMinutes;
    private final long resetMinIntervalSeconds;

    // Per-account throttle for forgot-password, so a known address can't be email-bombed
    // even if the gateway rate-limit is bypassed. Bounded, best-effort (single instance).
    private final PasswordResetThrottle resetThrottle = new PasswordResetThrottle();

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            LoginAttemptService loginAttemptService,
            TokenDenylistService tokenDenylistService,
            AuditLogService auditLogService,
            RefreshTokenService refreshTokenService,
            PasswordResetTokenService passwordResetTokenService,
            OutboxEventRecorder outboxRecorder,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenReuseResponder reuseResponder,
            @Value("${app.security.password-reset-token.expiration-minutes:30}") int resetExpiryMinutes,
            @Value("${app.security.password-reset.min-interval-seconds:60}") long resetMinIntervalSeconds
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.tokenDenylistService = tokenDenylistService;
        this.auditLogService = auditLogService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetTokenService = passwordResetTokenService;
        this.outboxRecorder = outboxRecorder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.reuseResponder = reuseResponder;
        this.resetExpiryMinutes = resetExpiryMinutes;
        this.resetMinIntervalSeconds = resetMinIntervalSeconds;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_EXISTS",
                    "Email is already registered."
            );
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim(),
                normalizePhone(request.phone()),
                Role.CUSTOMER,
                true
        );

        User savedUser = userRepository.save(user);
        log.info("Registration succeeded (email={}).", email);
        return buildAuthResponse(savedUser);
    }

    // Not @Transactional on purpose: failed-attempt accounting must commit even
    // though a failed login throws. LoginAttemptService owns those transactions.
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Instant now = Instant.now();

        // Reject a locked account before authenticating, so the lock can't be
        // worn down by continued guessing. Generic message → no user enumeration.
        if (loginAttemptService.isLocked(email, now)) {
            log.warn("Login blocked: account temporarily locked (email={}).", email);
            throw invalidCredentials();
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (AuthenticationException exception) {
            loginAttemptService.recordFailure(email, now);
            log.warn("Login failed (email={}).", email);
            throw invalidCredentials();
        }

        loginAttemptService.recordSuccess(email);

        User user = userRepository.findByEmail(email).orElseThrow(this::invalidCredentials);
        log.info("Login succeeded (email={}).", email);
        return buildAuthResponse(user);
    }

    /**
     * Server-side logout: revokes the caller's current access token so it is
     * rejected for the remainder of its lifetime (OWASP A07). Best-effort —
     * a token that can't be parsed leaves nothing to revoke.
     */
    /** Backward-compatible overload (revokes only the access token). */
    public void logout(String authorizationHeader, User actor) {
        logout(authorizationHeader, actor, null);
    }

    public void logout(String authorizationHeader, User actor, String refreshToken) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            String token = authorizationHeader.substring(BEARER_PREFIX.length());
            try {
                tokenDenylistService.revoke(jwtService.extractJti(token), jwtService.extractExpiration(token));
            } catch (JwtException | IllegalArgumentException exception) {
                log.debug("Logout: token could not be parsed for revocation ({}).",
                        exception.getClass().getSimpleName());
            }
        }
        // Additionally kill the whole refresh-token family if one was supplied (best-effort).
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeFamilyOf(refreshToken);
        }
        auditLogService.log(AuditEventType.LOGOUT, actor, "User", actor.getId(),
                "User logged out; access token revoked.");
        log.info("Logout: access token revoked (email={}).", actor.getEmail());
    }

    /**
     * Revokes a refresh-token family without needing a live access token — lets an idle
     * session (expired 15-min access token) still log out. Best-effort and idempotent;
     * always succeeds regardless of whether the token matched (anti-enumeration).
     */
    public void revoke(RefreshRequest request) {
        refreshTokenService.revokeFamilyOf(request.refreshToken());
    }

    /** Rotates a refresh token: issues a fresh access + refresh token, with reuse detection. */
    public AuthResponse refresh(RefreshRequest request) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(request.refreshToken());
        return AuthResponse.bearer(
                result.accessToken(),
                result.rawRefreshToken(),
                jwtService.getExpirationMinutes(),
                AuthUserResponse.from(result.user())
        );
    }

    /**
     * Requests a password reset. ALWAYS succeeds from the caller's view (the controller
     * returns one generic 200) — known, unknown and disabled accounts are indistinguishable,
     * both in the response body and (via {@code burnEquivalentWork}) in timing. Only an
     * enabled, existing, non-throttled account actually mints a token and emails it.
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> maybeUser = userRepository.findByEmail(email);

        if (maybeUser.isPresent() && maybeUser.get().isEnabled()
                && resetThrottle.allow(email, Instant.now(), resetMinIntervalSeconds)) {
            User user = maybeUser.get();
            try {
                String rawToken = passwordResetTokenService.issue(user);
                outboxRecorder.record(
                        "USER",
                        user.getId().toString(),
                        "PASSWORD_RESET_REQUESTED",
                        AuroraTopics.PASSWORD_RESET_REQUESTED,
                        user.getId().toString(),
                        PasswordResetRequestedEvent.of(
                                user.getId(), user.getEmail(), user.getFirstName(),
                                rawToken, resetExpiryMinutes)
                );
                auditLogService.log(AuditEventType.PASSWORD_RESET_REQUESTED, user, "USER",
                        user.getId(), "Password reset requested.");
            } catch (DataIntegrityViolationException race) {
                // Concurrent same-user requests can collide on the partial unique index —
                // collapse to the generic success path rather than leaking a 500.
                log.debug("Concurrent reset-token race for a user ({}).",
                        race.getClass().getSimpleName());
            }
        } else {
            // No account / disabled / throttled: pay the same CPU cost, write nothing.
            passwordResetTokenService.burnEquivalentWork();
        }
    }

    /**
     * Completes a password reset: validates + single-uses the token, re-hashes the new
     * password, and invalidates EVERY session (revokes all refresh-token families, which
     * also denylists their access tokens). No auto-login. Any failure → generic 401.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        UUID userId = passwordResetTokenService.consume(request.token());
        User user = userRepository.findById(userId)
                .filter(User::isEnabled)
                .orElseThrow(this::invalidResetToken);

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Kill every session: revoke each family AND denylist its outstanding access tokens.
        refreshTokenRepository.findDistinctFamilyIdByUserId(userId)
                .forEach(reuseResponder::revokeFamily);

        auditLogService.log(AuditEventType.PASSWORD_RESET, user, "USER", userId,
                "Password reset; all sessions invalidated.");
        log.info("Password reset succeeded (userId={}).", userId);
    }

    private BusinessException invalidResetToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_RESET_TOKEN",
                "Invalid or expired reset token.");
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user);
        String accessJti = jwtService.extractJti(accessToken);
        String refreshToken = refreshTokenService.issue(user, accessJti);
        return AuthResponse.bearer(
                accessToken,
                refreshToken,
                jwtService.getExpirationMinutes(),
                AuthUserResponse.from(user)
        );
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password."
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Optional field: trim, and store a blank phone as NULL (the @Pattern allows an empty string). */
    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }

    /**
     * Per-email forgot-password throttle: at most one issue per {@code minIntervalSeconds}.
     * Bounded in-memory map, best-effort (single instance) — a backstop behind the gateway
     * rate limit, not the primary control.
     */
    private static final class PasswordResetThrottle {
        private static final int CAP = 10_000;
        private final Map<String, Instant> lastIssue = new ConcurrentHashMap<>();

        boolean allow(String email, Instant now, long minIntervalSeconds) {
            if (lastIssue.size() > CAP) {
                Instant cutoff = now.minusSeconds(minIntervalSeconds);
                lastIssue.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
            }
            Instant previous = lastIssue.get(email);
            if (previous != null && previous.isAfter(now.minusSeconds(minIntervalSeconds))) {
                return false;
            }
            lastIssue.put(email, now);
            return true;
        }
    }
}
