package com.aurora.backend.user.entity;

import java.time.Duration;
import java.time.Instant;

import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the per-account login-lockout logic on {@link User} (OWASP A07).
 * Pure domain logic — no Spring context or database.
 */
class UserLockoutTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private User newUser() {
        return new User("ada@aurora.test", "irrelevant-hash", "Ada", "Lovelace", Role.CUSTOMER, true);
    }

    @Test
    void locksOnlyOnReachingTheThreshold() {
        User user = newUser();
        Instant now = Instant.now();

        assertThat(user.recordFailedLogin(3, WINDOW, now)).isFalse(); // attempt 1
        assertThat(user.recordFailedLogin(3, WINDOW, now)).isFalse(); // attempt 2
        assertThat(user.recordFailedLogin(3, WINDOW, now)).isTrue();  // attempt 3 -> locks

        assertThat(user.isLoginLocked(now)).isTrue();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
    }

    @Test
    void doesNotLockBelowTheThreshold() {
        User user = newUser();
        Instant now = Instant.now();

        user.recordFailedLogin(5, WINDOW, now);

        assertThat(user.isLoginLocked(now)).isFalse();
    }

    @Test
    void lockExpiresAfterTheWindow() {
        User user = newUser();
        Instant now = Instant.now();

        user.recordFailedLogin(1, WINDOW, now); // max=1 -> immediate lock

        assertThat(user.isLoginLocked(now)).isTrue();
        assertThat(user.isLoginLocked(now.plus(Duration.ofMinutes(16)))).isFalse();
    }

    @Test
    void resetClearsAttemptsAndLock() {
        User user = newUser();
        Instant now = Instant.now();
        user.recordFailedLogin(1, WINDOW, now);

        user.resetFailedLogins();

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isLoginLocked(now)).isFalse();
    }
}
