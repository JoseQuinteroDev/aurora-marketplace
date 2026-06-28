package com.aurora.backend.auth.dto;

/** The current MFA state for the authenticated user, for the SPA to render the security settings. */
public record MfaStatusResponse(
        boolean enabled
) {
}
