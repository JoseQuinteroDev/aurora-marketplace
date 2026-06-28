# TOTP MFA — implementation design (OWASP A07)

**Status:** design only — *deliberately not implemented overnight*. MFA touches the login /
token-issuance path (the app's highest-blast-radius surface), so it should be built in a
focused session with TDD + an adversarial review + the owner's PR review, not rushed
unsupervised. This spec makes that a mechanical, low-risk build. It is the last open item of
**Phase 1** in [`security-master-plan.md`](security-master-plan.md).

## Goal

Optional, opt-in TOTP (RFC 6238) second factor. **Additive and opt-in** — no existing user has
MFA, so the current login flow is unchanged for everyone until they enroll. That property is the
core safety guarantee: the new code path only runs for users who turned MFA on.

## Migration numbering

`V15` — this work stacks on `feat/prod-hardening` (which added `V13` optimistic-lock versions and
`V14` checkout idempotency). Branch `feat/mfa` off `feat/prod-hardening` so Flyway stays strictly
sequential (V13 → V14 → V15) and avoids an out-of-order/duplicate-version collision at merge.

## Schema (`V15`)

```sql
ALTER TABLE users ADD COLUMN mfa_enabled     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_secret      VARCHAR(255);          -- AES-GCM ciphertext (base64), null unless enrolling/enabled
ALTER TABLE users ADD COLUMN mfa_enrolled_at TIMESTAMPTZ;

-- Single-use, short-TTL login challenge (mirrors password_reset_tokens / email_verification_tokens)
CREATE TABLE mfa_challenges (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(64) NOT NULL,            -- SHA-256 of the opaque challenge token
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_mfa_challenges_token UNIQUE (token_hash)
);

-- One-time recovery codes (FOLLOW-UP — see "Deferred" below)
CREATE TABLE mfa_recovery_codes (
    id        UUID PRIMARY KEY,
    user_id   UUID NOT NULL REFERENCES users(id),
    code_hash VARCHAR(64) NOT NULL,              -- SHA-256 of the recovery code
    used_at   TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

## Crypto primitives (`security/mfa/`)

Implement in-house (no new dependency; portfolio-grade, RFC-test-vector verified):

1. **`Base32`** (RFC 4648, no padding) — encode/decode the secret. Test against RFC 4648 vectors.
2. **`TotpGenerator`** (RFC 6238) — HMAC-SHA1, 6 digits, 30 s step; `generate(secretBytes, instant)`
   and `verify(secret, code, instant, window=±1)`. Test against the **RFC 6238 published vectors**
   (seed `12345678901234567890`, known OTPs at fixed timestamps). The ±1 step window absorbs clock
   skew; do a **constant-time** code compare.
3. **`MfaSecretCipher`** — AES-256-GCM encrypt/decrypt of the Base32 secret for at-rest storage.
   Key from `app.security.mfa.encryption-key` (base64 32 bytes) with a dev default + a fail-fast
   validator on a placeholder in prod (mirror `JwtSecretValidator`). Random 12-byte IV per encrypt,
   stored with the ciphertext. Round-trip + tamper test.

## Enrollment flow (authenticated; login NOT yet gated — ship this increment first)

- `POST /api/auth/mfa/enroll` → generate a 160-bit secret, store it **encrypted** with
  `mfa_enabled` still **false** (pending). Return the Base32 secret + an `otpauth://totp/...` URI
  (the SPA renders a QR locally — do **not** generate the QR server-side; no image dep, and the
  secret never transits as an image). Idempotent re-enroll overwrites the pending secret.
- `POST /api/auth/mfa/confirm` `{code}` → verify `code` against the pending secret; on success set
  `mfa_enabled = true`, `mfa_enrolled_at = now`, (mint recovery codes — deferred). Audit `MFA_ENABLED`.
- `POST /api/auth/mfa/disable` `{code}` (or re-auth password) → verify, then null the secret +
  `mfa_enabled = false` + delete challenges/recovery codes. Audit `MFA_DISABLED`.
- `GET /api/auth/mfa/status` → `{enabled}` for the SPA to render state.

## Login gating (the careful increment — do LAST, with adversarial review)

- `AuthService.login`: after the password authenticates, **if `user.mfaEnabled`** → do **not** issue
  access/refresh tokens. Instead mint a single-use **challenge** (opaque random token, SHA-256 at
  rest in `mfa_challenges`, ~5 min TTL, bound to the user) and return `{ status: "MFA_REQUIRED",
  mfaToken: <opaque> }` (no tokens, no user PII beyond what's needed).
- `POST /api/auth/mfa/verify` `{mfaToken, code}` (**public**, like `/refresh`): load the challenge by
  hash, check not-expired + not-consumed, **consume it** (single-use), verify the TOTP (or a recovery
  code), then issue the real access + refresh tokens via the existing `buildAuthResponse`. Wrong code
  → generic 401, leave the challenge consumed (force a fresh login) **or** allow a small bounded
  number of attempts per challenge (decide: bounded attempts is friendlier; cap at 5 then invalidate).
- `SecurityConfig`: permit `POST /api/auth/mfa/verify` (public); require auth for
  `enroll`/`confirm`/`disable`/`status`.

### Security considerations (must hold)
- **No bypass:** the only token-issuing paths are `buildAuthResponse` (non-MFA login) and
  `/mfa/verify` (MFA login). Assert in tests that an MFA-enabled user's `login` returns NO tokens.
- **Challenge ≠ session:** the challenge token must not be accepted as an access/refresh token
  anywhere; it lives only in `mfa_challenges`. Single-use, short TTL, revoked on use.
- **Rate limit / lockout:** the existing per-account login lockout still applies at password step.
  Add a per-challenge attempt cap so the second factor can't be brute-forced within the 5-min window
  (10^6 space ÷ ±1 window ≈ 3×10^6 — a cap of 5 makes guessing negligible). Gateway: add `/mfa/verify`
  to the tight `/api/auth/**` bucket (already covered by the existing `Path=/api/auth/**` route).
- **Constant-time** TOTP/recovery comparison; **fixed-latency** isn't required (the code is the
  user's own secret, not an enumeration oracle), but the challenge-not-found vs bad-code responses
  must be an identical generic 401.
- **Replay:** ±1 step window means a code is valid ~90 s; acceptable for TOTP. Optionally record the
  last-accepted step per user to reject immediate replay (nice-to-have).
- **Reset/disable on credential change:** a password reset should also clear MFA challenges (already
  invalidates sessions); decide whether a reset disables MFA (recommend: keep MFA on).

## Frontend (after backend lands)
- Account/security settings: enroll (show QR from the otpauth URI + manual secret), confirm code,
  disable. Show recovery codes once (deferred).
- Login: when login returns `MFA_REQUIRED`, route to a code-entry step that calls `/mfa/verify` with
  the `mfaToken`; on success store tokens as today. i18n EN/ES.

## Test plan
- `Base32Test`, `TotpGeneratorTest` (RFC vectors), `MfaSecretCipherTest` (round-trip + tamper).
- `MfaServiceTest` (mock): enroll mints+encrypts pending; confirm enables only on a valid code;
  disable clears.
- `AuthServiceTest`: **MFA-enabled login returns MFA_REQUIRED and NO tokens**; `/mfa/verify` with a
  valid code issues tokens; invalid code / expired / consumed challenge → 401; non-MFA login
  unchanged (existing tests stay green).
- Testcontainers: challenge single-use (second verify with the same token fails); enroll→confirm→
  login-challenge→verify happy path end to end.
- Catalogue all in [`security-testing.md`](security-testing.md); flip A07 MFA rows to ✅.

## Deferred (fast-follow, not blockers)
- **Recovery codes** (lost-device escape hatch) — schema is above; mint 10 on confirm, show once,
  hashed at rest, one-time. Until then, a lost device = admin-assisted disable (document it).
- **Enforce-for-admins** — a policy gate that blocks `/api/admin/**` for an admin who hasn't enrolled
  (config `app.security.mfa.required-for-admins`). Needs an enrollment-nag UX; keep MFA opt-in first.
- **HttpOnly-cookie refresh storage** — tracked separately in Phase 1 (accepted residual today).
