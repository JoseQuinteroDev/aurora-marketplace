-- Optional, opt-in TOTP MFA (OWASP A07). Enrollment increment only: per-user MFA state on the
-- users table. The single-use login-challenge and recovery-code tables belong to the next
-- increment (see docs/appsec/mfa-design.md) and are intentionally NOT created here.
--
-- mfa_secret holds the AES-256-GCM ciphertext (base64) of the Base32 TOTP secret — never the
-- plaintext. It is null until a user begins enrollment, and is cleared again on disable.
-- mfa_enabled stays FALSE while an enrollment is pending (secret stored, not yet confirmed).
-- Additive + safe for existing rows: every current account defaults to MFA disabled.
ALTER TABLE users
    ADD COLUMN mfa_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mfa_secret      VARCHAR(255),
    ADD COLUMN mfa_enrolled_at TIMESTAMPTZ;
