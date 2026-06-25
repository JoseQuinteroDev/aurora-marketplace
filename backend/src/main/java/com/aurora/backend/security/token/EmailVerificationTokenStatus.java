package com.aurora.backend.security.token;

/** Lifecycle of an email-verification token: issued → used (single use) → or revoked. */
public enum EmailVerificationTokenStatus {
    ACTIVE,
    USED,
    REVOKED
}
