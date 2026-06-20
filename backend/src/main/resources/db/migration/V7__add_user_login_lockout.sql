-- Per-account brute-force protection (OWASP A07).
-- Tracks consecutive failed logins and a temporary lock window, enforced in the
-- commerce core (AuthService / LoginAttemptService) so the protection holds
-- regardless of ingress — unlike the gateway's per-IP limiter, which the core
-- bypasses when reached directly.

ALTER TABLE users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ;
