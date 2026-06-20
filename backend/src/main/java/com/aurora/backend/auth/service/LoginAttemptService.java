package com.aurora.backend.auth.service;

import java.time.Duration;
import java.time.Instant;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-account login throttling (OWASP A07). Counts consecutive failed logins and
 * locks the account for a cool-down window once a threshold is reached.
 *
 * <p>Failure accounting commits in its OWN transaction ({@code REQUIRES_NEW}).
 * This is deliberate: a failed login ultimately throws from {@code AuthService},
 * and were that to run inside a transaction the rollback would erase the
 * failed-attempt count and lock state — which MUST persist for the lockout to
 * mean anything. Committing the accounting independently of the request outcome
 * is the whole point.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(
            UserRepository userRepository,
            AuditLogService auditLogService,
            @Value("${app.security.login.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.security.login.lock-duration-minutes:15}") long lockDurationMinutes
    ) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockDurationMinutes);
    }

    @Transactional(readOnly = true)
    public boolean isLocked(String email, Instant now) {
        return userRepository.findByEmail(email)
                .map(user -> user.isLoginLocked(now))
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email, Instant now) {
        userRepository.findByEmail(email).ifPresent(user -> {
            boolean newlyLocked = user.recordFailedLogin(maxFailedAttempts, lockDuration, now);
            userRepository.save(user);
            if (newlyLocked) {
                log.warn("Account locked after {} consecutive failed login attempts (email={}).",
                        maxFailedAttempts, email);
                auditLogService.log(
                        AuditEventType.ACCOUNT_LOCKED,
                        user,
                        "User",
                        user.getId(),
                        "Account locked after " + maxFailedAttempts + " consecutive failed login attempts."
                );
            }
        });
    }

    @Transactional
    public void recordSuccess(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
                user.resetFailedLogins();
                userRepository.save(user);
            }
        });
    }
}
