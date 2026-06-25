package com.aurora.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank(message = "Verification token is required.")
        @Size(max = 200, message = "Verification token is invalid.")
        String token
) {
}
