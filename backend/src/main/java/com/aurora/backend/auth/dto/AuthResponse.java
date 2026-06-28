package com.aurora.backend.auth.dto;

/**
 * The auth result returned by login / register / refresh and MFA verify.
 *
 * <p>{@code status} discriminates the two login outcomes (OWASP A07): {@code AUTHENTICATED} carries
 * the real {@code Bearer} access + refresh tokens, while {@code MFA_REQUIRED} carries ONLY an opaque
 * {@code mfaToken} (the single-use login challenge) and no tokens — the client must exchange it at
 * {@code POST /api/auth/mfa/verify} with a current TOTP code to obtain tokens. Non-MFA flows always
 * return {@code AUTHENTICATED}, so the existing token fields are unchanged for every existing caller.
 */
public record AuthResponse(
        String status,
        String tokenType,
        String accessToken,
        String refreshToken,
        long expiresInMinutes,
        AuthUserResponse user,
        String mfaToken
) {

    public static AuthResponse bearer(
            String accessToken,
            String refreshToken,
            long expiresInMinutes,
            AuthUserResponse user
    ) {
        return new AuthResponse("AUTHENTICATED", "Bearer", accessToken, refreshToken,
                expiresInMinutes, user, null);
    }

    /**
     * A second-factor-required result: no access/refresh tokens are issued, only the opaque
     * single-use {@code mfaToken}. The client redeems it at {@code /api/auth/mfa/verify}.
     */
    public static AuthResponse mfaRequired(String mfaToken) {
        return new AuthResponse("MFA_REQUIRED", null, null, null, 0L, null, mfaToken);
    }
}
