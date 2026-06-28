package com.aurora.backend.security.token;

/** Lifecycle of a refresh-token row: issued → rotated (single use) → or revoked (logout/reuse). */
public enum RefreshTokenStatus {
    ACTIVE,
    ROTATED,
    REVOKED
}
