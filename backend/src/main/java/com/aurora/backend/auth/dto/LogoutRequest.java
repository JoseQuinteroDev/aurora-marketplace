package com.aurora.backend.auth.dto;

import jakarta.validation.constraints.Size;

/** Optional logout body: when a refresh token is supplied, its whole family is revoked too. */
public record LogoutRequest(
        @Size(max = 512, message = "Refresh token is invalid.")
        String refreshToken
) {
}
