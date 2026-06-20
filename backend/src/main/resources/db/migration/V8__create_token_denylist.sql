-- Server-side JWT revocation / logout (OWASP A07).
-- Stateless access tokens are otherwise valid until natural expiry. On logout
-- (and, in future, password-change / account-disable) a token's jti is recorded
-- here and checked by the auth filter on every request, so a stolen or
-- logged-out token can be invalidated before it expires. Rows self-prune once
-- their token has expired.

CREATE TABLE token_denylist (
    jti        UUID PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_token_denylist_expires_at ON token_denylist (expires_at);
