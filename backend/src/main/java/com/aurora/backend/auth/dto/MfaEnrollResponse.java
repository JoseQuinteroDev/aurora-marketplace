package com.aurora.backend.auth.dto;

/**
 * The one-time enrollment payload returned to the authenticated user who is turning MFA on. Carries
 * the Base32 TOTP secret (for manual entry) and the {@code otpauth://} provisioning URI (the SPA
 * renders it as a QR code locally). This is the ONLY response that ever exposes the secret — by
 * design, to the enrolling user, before it is confirmed and locked behind encryption at rest.
 */
public record MfaEnrollResponse(
        String secret,
        String otpauthUri
) {
}
