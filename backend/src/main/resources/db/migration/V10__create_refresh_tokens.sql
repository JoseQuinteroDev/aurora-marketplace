-- Refresh-token rotation with automatic reuse detection (OWASP A07).
-- Opaque refresh tokens are NEVER stored in plaintext: each row stores only the
-- SHA-256 hash of a full-entropy 256-bit secret. The raw value handed to the
-- client is "rowId.secret" so lookup is an indexed PK fetch followed by a
-- constant-time hash compare (no hash-column scan, no enumeration/timing leak).
-- Each row is single-use: on POST /api/auth/refresh it is rotated to a child in
-- the SAME family. Presenting an already-rotated token outside a short grace
-- window is treated as theft and the whole family is revoked. Rows self-prune
-- after expiry, mirroring token_denylist. ddl-auto stays validate.

CREATE TABLE refresh_tokens (
    id                UUID PRIMARY KEY,
    family_id         UUID NOT NULL,
    user_id           UUID NOT NULL,
    token_hash        VARCHAR(64) NOT NULL,
    parent_id         UUID,
    replaced_by_id    UUID,
    issued_access_jti UUID,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at        TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rotated_at        TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- One ACTIVE token per family, enforced at the DB so the rotation race fails
-- closed even if the application guard ever regresses.
CREATE UNIQUE INDEX uk_refresh_tokens_active_family
    ON refresh_tokens (family_id) WHERE status = 'ACTIVE';
