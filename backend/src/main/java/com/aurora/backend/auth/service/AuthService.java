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
import com.aurora.backend.auth.dto.ResendVerificationRequest;
import com.aurora.backend.auth.dto.ResetPasswordRequest;
import com.aurora.backend.auth.dto.VerifyEmailRequest;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.messaging.AuroraTopics;
import com.aurora.backend.messaging.event.EmailVerificationRequestedEvent;
import com.aurora.backend.messaging.event.PasswordResetRequestedEvent;
import com.aurora.backend.messaging.outbox.OutboxEventRecorder;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.security.password.PasswordBreachChecker;
import com.aurora.backend.security.token.EmailVerificationTokenService;
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
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final PasswordBreachChecker passwordBreachChecker;
    private final int resetExpiryMinutes;
    private final long resetMinIntervalSeconds;
    private final long resetLatencyFloorMs;
    private final int verifyExpiryMinutes;
    private final long verifyMinIntervalSeconds;
    private final long verifyLatencyFloorMs;

    // Per-account throttles. Forgot-password and resend-verification use SEPARATE instances so
    // a request to one does not consume the other's window for the same email. Bounded, best-effort.
    private final PasswordResetThrottle resetThrottle = new PasswordResetThrottle();
    private final PasswordResetThrottle verificationThrottle = new PasswordResetThrottle();

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
            EmailVerificationTokenService emailVerificationTokenService,
            OutboxEventRecorder outboxRecorder,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenReuseResponder reuseResponder,
            PasswordBreachChecker passwordBreachChecker,
            @Value("${app.security.password-reset-token.expiration-minutes:30}") int resetExpiryMinutes,
            @Value("${app.security.password-reset.min-interval-seconds:60}") long resetMinIntervalSeconds,
            @Value("${app.security.password-reset.latency-floor-ms:350}") long resetLatencyFloorMs,
            @Value("${app.security.email-verification-token.expiration-minutes:1440}") int verifyExpiryMinutes,
            @Value("${app.security.email-verification.min-interval-seconds:60}") long verifyMinIntervalSeconds,
            @Value("${app.security.email-verification.latency-floor-ms:350}") long verifyLatencyFloorMs
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
        this.emailVerificationTokenService = emailVerificationTokenService;
        this.outboxRecorder = outboxRecorder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.reuseResponder = reuseResponder;
        this.passwordBreachChecker = passwordBreachChecker;
        this.resetExpiryMinutes = resetExpiryMinutes;
        this.resetMinIntervalSeconds = resetMinIntervalSeconds;
        this.resetLatencyFloorMs = resetLatencyFloorMs;
        this.verifyExpiryMinutes = verifyExpiryMinutes;
        this.verifyMinIntervalSeconds = verifyMinIntervalSeconds;
        this.verifyLatencyFloorMs = verifyLatencyFloorMs;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Breach check FIRST, before any DB access. register() is @Transactional but the JDBC
        // connection is acquired lazily on the first query (Hibernate AS_NEEDED), so running the
        // blocking HIBP HTTP call before existsByEmail means no pooled connection is pinned during
        // it — an HIBP slowdown can't starve the connection pool. (Same ordering as resetPassword.)
        assertPasswordNotBreached(request.password());

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
        issueVerification(savedUser);
        log.info("Registration succeeded (email={}).", email);
        return buildAuthResponse(savedUser);
    }

    /**
     * Mints + emails an email-verification token at registration. Uses the IN-CURRENT-TRANSACTION
     * issue variant (NOT REQUIRES_NEW): the user row is saved-but-not-committed, so the token's
     * FK is only satisfiable on the same connection. Token + outbox + audit commit atomically with
     * the user INSERT — an outbox failure rolls the whole registration back rather than 201-ing a
     * user who will never get a verification email.
     */
    private void issueVerification(User user) {
        String rawToken = emailVerificationTokenService.issueInCurrentTransaction(user);
        outboxRecorder.record(
                "USER",
                user.getId().toString(),
                "EMAIL_VERIFICATION_REQUESTED",
                AuroraTopics.EMAIL_VERIFICATION_REQUESTED,
                user.getId().toString(),
                EmailVerificationRequestedEvent.of(
                        user.getId(), user.getEmail(), user.getFirstName(), rawToken, verifyExpiryMinutes)
        );
        auditLogService.log(AuditEventType.EMAIL_VERIFICATION_REQUESTED, user, "USER",
                user.getId(), "Email verification requested at registration.");
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
     * returns one generic 200): known, unknown and disabled accounts are indistinguishable
     * in the response body, and a fixed latency floor makes them indistinguishable in timing
     * too (so the positive branch's extra DB writes are not an enumeration oracle). The
     * per-account throttle is evaluated for EVERY email so it cannot itself become a
     * fast/slow side-channel. Only an enabled, existing, non-throttled account mints a token.
     *
     * <p>Deliberately not {@code @Transactional}: the mint runs in its own (REQUIRES_NEW)
     * transaction and the outbox/audit writes are each atomic, so the latency-floor sleep
     * never holds a DB transaction or connection open.
     */
    public void requestPasswordReset(ForgotPasswordRequest request) {
        long startNanos = System.nanoTime();
        String email = normalizeEmail(request.email());
        // Throttle EVERY email (known or not) so a probe is throttled identically either way.
        boolean throttled = !resetThrottle.allow(email, Instant.now(), resetMinIntervalSeconds);
        Optional<User> maybeUser = userRepository.findByEmail(email);

        if (!throttled && maybeUser.isPresent() && maybeUser.get().isEnabled()) {
            User user = maybeUser.get();
            try {
                // issue() is REQUIRES_NEW, so a partial-unique-index collision rolls back only
                // its own tx and is catchable here as a clean generic success (never a 500).
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
                log.debug("Concurrent reset-token race for a user ({}).",
                        race.getClass().getSimpleName());
            }
        } else {
            // No account / disabled / throttled: pay the same CPU hash cost, write nothing.
            passwordResetTokenService.burnEquivalentWork();
        }

        equalizeLatency(startNanos, resetLatencyFloorMs);
    }

    /** Sleeps until a fixed floor so every anti-enumeration response takes the same minimum time. */
    private void equalizeLatency(long startNanos, long floorMs) {
        long remainingMs = floorMs - (System.nanoTime() - startNanos) / 1_000_000L;
        if (remainingMs > 0) {
            try {
                Thread.sleep(remainingMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Completes a password reset: validates + single-uses the token, re-hashes the new
     * password, and invalidates EVERY session (revokes all refresh-token families, which
     * also denylists their access tokens). No auto-login. Any failure → generic 401.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Validate the chosen password BEFORE consuming the token, so a breached-password
        // rejection never burns a valid reset token (the user can retry with the same link).
        assertPasswordNotBreached(request.newPassword());

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

    /**
     * Verifies an account's email from a single-use token. Verification is a soft per-action
     * state (it gates only order placement), NOT a credential change — so no session is revoked
     * and the user is not auto-logged-in. Idempotent: re-verifying is a no-op success; any bad
     * token is a generic 401.
     */
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        UUID userId = emailVerificationTokenService.consume(request.token());
        User user = userRepository.findById(userId)
                .filter(User::isEnabled)
                .orElseThrow(this::invalidVerificationToken);

        if (!user.isEmailVerified()) {
            user.verifyEmail();
            userRepository.save(user);
            auditLogService.log(AuditEventType.EMAIL_VERIFIED, user, "USER", userId, "Email verified.");
            log.info("Email verified (userId={}).", userId);
        }
    }

    /**
     * Re-sends the verification email. Anti-enumeration, mirroring {@link #requestPasswordReset}:
     * always succeeds from the caller's view (the controller returns one generic 200); the throttle
     * is evaluated for EVERY email and a fixed latency floor hides the positive branch's DB writes.
     * The already-verified case takes the same no-op branch, so "is this verified?" leaks nothing.
     */
    public void resendVerification(ResendVerificationRequest request) {
        long startNanos = System.nanoTime();
        String email = normalizeEmail(request.email());
        boolean throttled = !verificationThrottle.allow(email, Instant.now(), verifyMinIntervalSeconds);
        Optional<User> maybeUser = userRepository.findByEmail(email);

        if (!throttled && maybeUser.isPresent() && maybeUser.get().isEnabled() && !maybeUser.get().isEmailVerified()) {
            User user = maybeUser.get();
            try {
                // No outer tx here, so REQUIRES_NEW issue() is correct (and a race is catchable).
                String rawToken = emailVerificationTokenService.issue(user);
                outboxRecorder.record(
                        "USER",
                        user.getId().toString(),
                        "EMAIL_VERIFICATION_REQUESTED",
                        AuroraTopics.EMAIL_VERIFICATION_REQUESTED,
                        user.getId().toString(),
                        EmailVerificationRequestedEvent.of(
                                user.getId(), user.getEmail(), user.getFirstName(), rawToken, verifyExpiryMinutes)
                );
                auditLogService.log(AuditEventType.EMAIL_VERIFICATION_REQUESTED, user, "USER",
                        user.getId(), "Email verification re-requested.");
            } catch (DataIntegrityViolationException race) {
                log.debug("Concurrent verification-token race for a user ({}).",
                        race.getClass().getSimpleName());
            }
        } else {
            // No account / disabled / already-verified / throttled: pay the hash cost, write nothing.
            emailVerificationTokenService.burnEquivalentWork();
        }

        equalizeLatency(startNanos, verifyLatencyFloorMs);
    }

    private BusinessException invalidVerificationToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_VERIFICATION_TOKEN",
                "Invalid or expired verification link.");
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

    /**
     * Rejects a password known to have appeared in a public data breach (OWASP A07 —
     * credential hygiene). Fail-open: a breach-corpus outage never blocks the user. Applied
     * only where the user CHOOSES a password (register / reset), never on login — checking a
     * login password would leak nothing useful and would couple sign-in to an external call.
     */
    private void assertPasswordNotBreached(String rawPassword) {
        if (passwordBreachChecker.isBreached(rawPassword)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_BREACHED",
                    "This password has appeared in a known data breach. Please choose a different one."
            );
        }
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
