-- Single-use, short-TTL TOTP login challenge (OWASP A07) — the MFA login-gating increment.
-- When an mfa_enabled user passes the password step, AuthService issues NO access/refresh tokens;
-- it mints a challenge here and returns {status: MFA_REQUIRED, mfaToken}. POST /api/auth/mfa/verify
-- then exchanges that token + a current TOTP code for the real tokens (the only MFA-login path).
--
-- Mirrors password_reset_tokens / refresh_tokens: only the SHA-256 hash of a full-entropy secret is
-- stored; the raw "rowId.secret" value is returned to the client exactly once, so lookup is an
-- indexed PK fetch + a constant-time hash compare (no hash-column scan / enumeration / timing leak).
-- Single-use is enforced by consumed_at (NULL = still claimable); a bounded attempts counter caps
-- second-factor brute force within the TTL window (invalidated by setting consumed_at at the cap).
-- ddl-auto stays validate, so the @Entity must match this exactly.

CREATE TABLE mfa_challenges (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,            -- SHA-256 of the opaque challenge secret
    attempts    INT NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_mfa_challenges_token UNIQUE (token_hash),
    CONSTRAINT fk_mfa_challenges_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_mfa_challenges_user_id ON mfa_challenges (user_id);
CREATE INDEX idx_mfa_challenges_expires_at ON mfa_challenges (expires_at);
