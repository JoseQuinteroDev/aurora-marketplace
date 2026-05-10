package com.aurora.backend.auth.dto;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresInMinutes,
        AuthUserResponse user
) {

    public static AuthResponse bearer(String accessToken, long expiresInMinutes, AuthUserResponse user) {
        return new AuthResponse("Bearer", accessToken, expiresInMinutes, user);
    }
}
