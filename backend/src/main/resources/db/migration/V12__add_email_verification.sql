-- Email verification: orthogonal to `enabled` (which stays the admin/lockout/auth flag).
-- New accounts start unverified; all PRE-FEATURE accounts are grandfathered verified so
-- the checkout gate never retroactively locks out existing customers. New rows default FALSE.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Grandfather every existing account (unconditional — all current rows predate the feature).
UPDATE users SET email_verified = TRUE;

-- Column-for-column clone of V11 (password_reset_tokens) so the proven entity/DDL match holds.
CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    used_at     TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT uk_email_verification_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_email_verification_tokens_user_id    ON email_verification_tokens (user_id);
CREATE INDEX idx_email_verification_tokens_expires_at ON email_verification_tokens (expires_at);

-- Fail-closed backstop: at most one ACTIVE verification token per user.
CREATE UNIQUE INDEX uk_email_verification_tokens_active_user
    ON email_verification_tokens (user_id) WHERE status = 'ACTIVE';
