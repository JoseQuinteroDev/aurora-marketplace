package com.aurora.backend.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoginAttemptService}: the per-account login lockout (OWASP A07).
 * Asserts the threshold/cool-down config is forwarded verbatim to the {@link User} aggregate,
 * the lock transition is persisted and audited only when newly locked, the lock window is
 * honoured, and a successful login resets the failure tracking.
 */
class LoginAttemptServiceTest {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(LOCK_DURATION_MINUTES);
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000aa");
    private static final String EMAIL = "ada@aurora.test";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final LoginAttemptService service =
            new LoginAttemptService(userRepository, auditLogService, MAX_FAILED_ATTEMPTS, LOCK_DURATION_MINUTES);

    private User mockUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        return user;
    }

    // --- isLocked: respects the lock window ---

    @Test
    void isLockedReturnsTrueWhileTheLockWindowIsStillOpen() {
        Instant now = Instant.parse("2026-06-28T10:00:00Z");
        User locked = mockUser();
        when(locked.isLoginLocked(now)).thenReturn(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(locked));

        assertThat(service.isLocked(EMAIL, now)).isTrue();
    }

    @Test
    void isLockedReturnsFalseOnceTheLockWindowHasElapsed() {
        Instant now = Instant.parse("2026-06-28T10:00:00Z");
        User notLocked = mockUser();
        when(notLocked.isLoginLocked(now)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(notLocked));

        assertThat(service.isLocked(EMAIL, now)).isFalse();
    }

    @Test
    void isLockedReturnsFalseForAnUnknownAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(service.isLocked(EMAIL, Instant.now())).isFalse();
    }

    // --- recordFailure: increments, locks at the configured threshold, audits once ---

    @Test
    void recordFailureForwardsTheConfiguredThresholdAndWindowToTheUserThenSaves() {
        Instant now = Instant.parse("2026-06-28T10:00:00Z");
        User user = mockUser();
        // Not the locking failure: returns false, so no audit.
        when(user.recordFailedLogin(MAX_FAILED_ATTEMPTS, LOCK_DURATION, now)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.recordFailure(EMAIL, now);

        // The exact max-failed-attempts (5) and lock-duration (15m) are passed through unchanged —
        // a regression that hard-codes or drops either would fail this verification.
        verify(user).recordFailedLogin(MAX_FAILED_ATTEMPTS, LOCK_DURATION, now);
        verify(userRepository).save(user);
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void recordFailureAuditsAnAccountLockOnlyWhenThisFailureTriggersTheLock() {
        Instant now = Instant.parse("2026-06-28T10:00:00Z");
        User user = mockUser();
        // The threshold-reaching failure: the aggregate reports it newly locked the account.
        when(user.recordFailedLogin(MAX_FAILED_ATTEMPTS, LOCK_DURATION, now)).thenReturn(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.recordFailure(EMAIL, now);

        verify(userRepository).save(user);
        verify(auditLogService).log(eq(AuditEventType.ACCOUNT_LOCKED), eq(user), any(), eq(USER_ID), any());
    }

    @Test
    void recordFailureForAnUnknownAccountIsANoOp() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service.recordFailure(EMAIL, Instant.now());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    // --- recordSuccess: resets the failure counter ---

    @Test
    void recordSuccessResetsFailuresWhenThereWerePriorFailedAttempts() {
        User user = mockUser();
        when(user.getFailedLoginAttempts()).thenReturn(3);
        when(user.getLockedUntil()).thenReturn(null);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.recordSuccess(EMAIL);

        verify(user).resetFailedLogins();
        verify(userRepository).save(user);
    }

    @Test
    void recordSuccessResetsWhenTheAccountStillCarriesALockTimestamp() {
        User user = mockUser();
        when(user.getFailedLoginAttempts()).thenReturn(0);
        when(user.getLockedUntil()).thenReturn(Instant.parse("2026-06-28T10:15:00Z"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.recordSuccess(EMAIL);

        verify(user).resetFailedLogins();
        verify(userRepository).save(user);
    }

    @Test
    void recordSuccessIsANoOpWhenThereIsNothingToReset() {
        User user = mockUser();
        when(user.getFailedLoginAttempts()).thenReturn(0);
        when(user.getLockedUntil()).thenReturn(null);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.recordSuccess(EMAIL);

        verify(user, never()).resetFailedLogins();
        verify(userRepository, never()).save(any());
    }

    // --- threshold behaviour exercised against the real User aggregate (no stubbing) ---

    @Test
    void locksExactlyAtTheConfiguredMaxFailedAttemptsAgainstARealUser() {
        Instant now = Instant.parse("2026-06-28T10:00:00Z");
        User realUser = new User(EMAIL, "hash", "Ada", "Lovelace",
                com.aurora.backend.user.role.Role.CUSTOMER, true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(realUser));

        // The first (max - 1) failures accumulate but must NOT lock the account.
        for (int i = 1; i < MAX_FAILED_ATTEMPTS; i++) {
            service.recordFailure(EMAIL, now);
            assertThat(realUser.isLoginLocked(now)).isFalse();
            assertThat(realUser.getFailedLoginAttempts()).isEqualTo(i);
        }

        // The max-th failure crosses the threshold and locks for exactly the configured window.
        service.recordFailure(EMAIL, now);
        assertThat(realUser.getFailedLoginAttempts()).isEqualTo(MAX_FAILED_ATTEMPTS);
        assertThat(realUser.isLoginLocked(now)).isTrue();
        assertThat(realUser.getLockedUntil()).isEqualTo(now.plus(LOCK_DURATION));
        // Lock is temporary: it clears once the window elapses.
        assertThat(realUser.isLoginLocked(now.plus(LOCK_DURATION).plusSeconds(1))).isFalse();

        // Exactly one ACCOUNT_LOCKED audit across the whole sequence (only the transition is audited).
        verify(auditLogService).log(eq(AuditEventType.ACCOUNT_LOCKED), eq(realUser), any(), any(), any());

        // A subsequent successful login wipes the failure tracking entirely.
        service.recordSuccess(EMAIL);
        assertThat(realUser.getFailedLoginAttempts()).isZero();
        assertThat(realUser.getLockedUntil()).isNull();
    }

    @Test
    void doesNotLockBeforeReachingTheThresholdAgainstARealUser() {
        Instant now = Instant.parse("2026-06-28T10:00:00Z");
        User realUser = new User(EMAIL, "hash", "Ada", "Lovelace",
                com.aurora.backend.user.role.Role.CUSTOMER, true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(realUser));

        for (int i = 1; i < MAX_FAILED_ATTEMPTS; i++) {
            service.recordFailure(EMAIL, now);
        }

        assertThat(realUser.isLoginLocked(now)).isFalse();
        assertThat(realUser.getLockedUntil()).isNull();
        // No lock crossed → no ACCOUNT_LOCKED audit at all.
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }
}
