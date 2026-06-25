package com.aurora.backend.auth.dto;

public record AuthResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        long expiresInMinutes,
        AuthUserResponse user
) {

    public static AuthResponse bearer(
            String accessToken,
            String refreshToken,
            long expiresInMinutes,
            AuthUserResponse user
    ) {
        return new AuthResponse("Bearer", accessToken, refreshToken, expiresInMinutes, user);
    }
}
