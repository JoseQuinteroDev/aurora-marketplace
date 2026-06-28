package com.aurora.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/mfa/verify} (public): the opaque single-use {@code mfaToken} from a
 * {@code MFA_REQUIRED} login, plus the current 6-digit TOTP code. The code is constrained to exactly
 * six digits — the only shape a valid RFC 6238 code can take — so malformed input is rejected at the
 * edge (400 VALIDATION_ERROR) before reaching the service.
 */
public record VerifyMfaRequest(
        @NotBlank(message = "MFA token is required.")
        @Size(max = 512, message = "MFA token is invalid.")
        String mfaToken,

        @NotBlank(message = "Verification code is required.")
        @Pattern(regexp = "\\d{6}", message = "Verification code must be 6 digits.")
        String code
) {
}
