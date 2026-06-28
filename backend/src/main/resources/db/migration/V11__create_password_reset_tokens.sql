-- Self-service password reset tokens (OWASP A07 hygiene).
-- Mirrors refresh_tokens: only the SHA-256 hash of a full-entropy 256-bit secret
-- is stored; the raw value handed to the user is "rowId.secret", so lookup is an
-- indexed PK fetch followed by a constant-time hash compare (no hash-column scan,
-- no enumeration/timing leak). Single-use, short-lived (default 30 min) and
-- self-pruning. Every validation failure collapses to one generic error.
-- ddl-auto stays validate, so the @Entity must match this exactly.

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    used_at     TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT uk_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);

-- Fail-closed backstop: at most one ACTIVE reset token per user, even if the
-- application guard ever regresses (mirrors uk_refresh_tokens_active_family).
CREATE UNIQUE INDEX uk_password_reset_tokens_active_user
    ON password_reset_tokens (user_id) WHERE status = 'ACTIVE';
