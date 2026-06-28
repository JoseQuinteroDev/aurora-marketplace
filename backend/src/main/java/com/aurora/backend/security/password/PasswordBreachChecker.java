package com.aurora.backend.security.password;

/**
 * Checks a candidate plaintext password against a corpus of passwords known to have
 * appeared in public data breaches (OWASP A07 — credential hygiene). Used at the points
 * where a user CHOOSES a password (registration, password reset) so a credential-stuffing
 * list entry can't become a live account password.
 *
 * <p>Implementations must be <strong>fail-open</strong>: if the breach corpus is
 * unreachable, return {@code false} (treat the password as acceptable) rather than block
 * the user. Availability of registration/reset outweighs strict enforcement, and the other
 * auth controls (lockout, rotation, short access TTL) still apply.
 */
public interface PasswordBreachChecker {

    /**
     * @return {@code true} only if the password is known to be breached. Returns
     *         {@code false} for an empty/blank password, when checking is disabled, and
     *         on any error talking to the breach corpus (fail-open).
     */
    boolean isBreached(String rawPassword);
}
