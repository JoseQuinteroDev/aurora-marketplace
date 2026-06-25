package com.aurora.backend.security.token;

/** Lifecycle of a password-reset token: issued → used (single use) → or revoked. */
public enum PasswordResetTokenStatus {
    ACTIVE,
    USED,
    REVOKED
}
