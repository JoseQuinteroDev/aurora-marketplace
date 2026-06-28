package com.aurora.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * A user-supplied TOTP code for confirming enrollment or disabling MFA. Constrained to exactly six
 * digits — the only shape a valid RFC 6238 code can take — so malformed input is rejected at the
 * edge (400 VALIDATION_ERROR) before reaching the service.
 */
public record MfaCodeRequest(
        @NotBlank(message = "Verification code is required.")
        @Pattern(regexp = "\\d{6}", message = "Verification code must be 6 digits.")
        String code
) {
}
