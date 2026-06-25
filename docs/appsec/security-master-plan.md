# Aurora Marketplace — Master Security Plan

**Goal:** drive Aurora to a state where every OWASP Top 10 (2021) category is closed
by a **control in code + a regression test + a detection signal**, and where that
state is *continuously re-verified* (SAST/SCA/DAST + manual pentest) so it cannot
silently regress. "Unbreakable" is not a finish line — it is a posture of **defense
in depth** plus a **verify loop** that catches drift.

This plan consolidates and orders the gaps already tracked across
[`README.md`](README.md) (posture), [`owasp-top-10.md`](owasp-top-10.md),
[`security-testing.md`](security-testing.md), [`threat-model.md`](threat-model.md),
[`vulnerable-lab.md`](vulnerable-lab.md) and
[`../devops/cicd-security.md`](../devops/cicd-security.md) into a single execution
roadmap.

---

## 0. Operating principles

1. **Defense in depth** — edge (gateway) → app (core) → data → messaging → ops. No
   single failure is catastrophic.
2. **Never trust the client** — prices, totals, stock, roles, ownership and authz
   are always recomputed/reloaded server-side. (Already enforced; locked by tests.)
3. **Every control has a test and a detection** — a control with no regression test
   can silently break; a control with no signal can be bypassed unnoticed.
4. **A few hard gates, everything else visible** — over-gating trains people to
   bypass the pipeline; under-gating lets risk through.
5. **Offense informs defense** — the `vulnerable-lab` branch proves each control by
   breaking it, then proves the fix. Burp/ZAP confirm it on the running app.

## 1. Baseline (what already protects Aurora)

Strong controls already in place — the plan *builds on* these, it does not redo them:

- **AuthN/Z:** stateless JWT; authorities reloaded from the **DB** (not the token
  claim) in `SecurityConfig.userDetailsService`; `JwtService.verifyWith` pins the
  algorithm; `TokenDenylistService` revokes on logout; `JwtSecretValidator`
  fail-fast on a placeholder prod secret; per-account lockout (`LoginAttemptService`).
- **Business integrity:** server-side recomputation of money/stock/discounts in
  `CheckoutService`/`PaymentService`; owner-scoped reads (`findByIdAndUserId`).
- **Messaging:** transactional outbox (no dual-write), consumer idempotency
  (`ProcessedEventTracker`), exponential backoff + per-topic DLTs.
- **Edge:** per-client-IP Redis rate limiting, Resilience4j circuit breaker/timeouts,
  JSON fallbacks (gateway).
- **Headers:** CSP/Referrer-Policy/HSTS/X-Frame/X-Content-Type/Permissions-Policy
  on the core (`SecurityConfig`).
- **Verify loop:** ~128 tests (60+ security-focused), CI scanners (CodeQL, Trivy,
  Gitleaks **hard gate**, Hadolint, SBOM, dependency-review), opt-in DAST
  (NightVision), audit log + Prometheus + tracing.

## 2. The roadmap (ordered for execution)

> Each phase lists **what**, the **OWASP** categories it closes, and the **exit
> criteria**. Do them in order — earlier phases make later ones enforceable.

### Phase 0 — Make the gates real *(start here)*
*OWASP: meta / A05 / A08 — turns documented gates into enforced ones.*
- Merge the `feat/security-test-hardening` PR into `main` (60+ security tests, CI
  frontend gate, dependency-review, core security headers).
- **Branch protection on `main`:** require `CI`, `secret-scan`, `dependency-review`
  status checks + 1 review + up-to-date branch; block force-push and deletion.
- **SHA-pin every GitHub Action** to a full commit SHA (let the `github-actions`
  Dependabot ecosystem bump them).
- **Exit:** a red `secret-scan` / failing test physically cannot merge to `main`.

### Phase 1 — Authentication & session hardening
*OWASP: A07 (auth failures), A01.*
- **Refresh-token rotation:** short-lived access token + rotating refresh token with
  **reuse detection** (a replayed refresh revokes the family). Persist refresh-token
  state; reuse the `jti` denylist machinery.
- **Token storage:** offer `HttpOnly` + `Secure` + `SameSite=Strict` cookie storage
  (with CSRF defense for cookie mode) as an alternative to `localStorage`.
- **Account recovery:** password reset + email verification using single-use,
  time-boxed, hashed tokens (never log or email the raw token twice).
- **Credential hygiene:** breached-password check (HIBP range/k-anonymity API) at
  register/reset; optional **TOTP MFA** for admins.
- **Exit:** auth-lifecycle gaps in `README.md` move from ❌/⚠️ to ✅; each flow has a
  regression test and an audit-log entry.

### Phase 2 — Edge & transport hardening
*OWASP: A05 (misconfig), A07, A01.*
- **CORS per environment:** replace the hardcoded `localhost:*` + credentials with an
  env-driven allow-list; reject unexpected origins (test it).
- **Rate limiting, tiered:** keep the per-IP gateway bucket; add a **stricter bucket
  on `/api/auth/**`** and a per-authenticated-user bucket; document the per-account
  lockout as the non-bypassable backstop. Return `429` with `Retry-After`.
- **Gateway-edge security headers:** mirror the core's header set at the gateway so
  non-core and error/fallback responses also carry them.
- **Lock down the gateway:** reactive Spring Security in front of it; bind management
  endpoints to a separate non-routable port; drop `show-details: always`.
- **TLS everywhere:** HTTPS at the edge (so HSTS engages) and TLS for
  Postgres/Redis/Kafka/SMTP via a hardened compose/deploy overlay.
- **Exit:** a DAST scan reports no missing-header / permissive-CORS / unthrottled
  findings; management surface is not publicly routable.

### Phase 3 — Data & business-logic integrity
*OWASP: A04 (insecure design), A08.*
- **Optimistic locking (`@Version`)** on `inventory`, `order`, `payment` to close
  races beyond the current row-lock (concurrent checkout/refund).
- **Checkout idempotency key:** client-supplied `Idempotency-Key` header → unique
  constraint, so a double-submit/replay can't create two orders or double-charge.
- **Coupon integrity:** DB unique constraint on code + atomic usage counting so
  limits can't be beaten by concurrency.
- **Exit:** new Testcontainers integration tests prove no oversell / no double-order
  under concurrency.

### Phase 4 — Injection, deserialization & SSRF sweep
*OWASP: A03 (injection), A08, A10 (SSRF).*
- **Injection audit:** confirm 100% parameterized JPA (no string-concatenated
  queries); add a CodeQL/grep gate flagging concatenated query strings.
- **SSRF guard:** validate/allow-list any server-fetched URL (product image URLs,
  future webhooks); block internal/link-local targets.
- **Deserialization:** keep strict JSON mapping (`@JsonIgnoreProperties`); no generic
  polymorphic deserialization of untrusted input.
- **Header/CRLF injection:** confirm email is built via structured API, not manual
  header concatenation (already true — lock with a test).
- **Exit:** lab 04 (SQLi) exploit fails on `main`; SAST shows no injection sinks.

### Phase 5 — Continuous verification (SAST · SCA · DAST · pentest)
*OWASP: all — this is the loop that keeps the above honest. DevSecOps core.*
- **SAST/SCA (CI):** CodeQL + Trivy already run; add **`trivy image`** on built
  images and **sign the SBOM**.
- **DAST in CI:** NightVision wired (opt-in). Run it on a schedule against the full
  `--profile apps` stack.
- **Manual pentest with Burp Suite** — see §4 for the concrete methodology. Run the
  full `security-testing.md` §4 checklist each release.
- **Offensive training ground:** finish `vulnerable-lab` labs **04–08** (SQLi,
  `alg=none`, verbose errors, permissive CORS, no rate-limit) so each control has a
  living exploit→fix proof.
- **Exit:** every release has a recorded DAST pass + checklist sign-off; every fixed
  finding ships with a regression test.

### Phase 6 — Secrets & supply chain
*OWASP: A02 (crypto/secrets), A05, A08.*
- **Secrets:** no inline dev defaults in any non-local profile; externalize via
  env/secret manager; define a rotation procedure; keep Gitleaks as the hard gate.
- **Supply chain:** **image signing + provenance** (cosign + SLSA build-provenance,
  attest the SBOM); **pin base images by digest**; narrow SCA gating to *net-new
  CRITICAL* application CVEs.
- **Runtime:** containers already non-root — add read-only root FS + dropped Linux
  capabilities in the hardened overlay.
- **Exit:** a consumer can verify a deployed image is the genuine CI output; no
  floating tags.

### Phase 7 — Detection & response
*OWASP: A09 (logging & monitoring failures).*
- **Centralize** security-event logs (auth outcomes, 401/403 denials, JWT rejections,
  lockouts, 500s — already emitted) into a queryable store.
- **Alert** on anomalies: authz-denial spikes, `429` bursts, DLT growth, and the
  *impossible* signal (a request authorized as `ADMIN` whose DB role is `CUSTOMER`).
- **Dashboards** (Prometheus already) + alerting rules; an **incident runbook** and
  the `SECURITY.md` disclosure policy kept current.
- **Exit:** each lab exploit in §5 produces a detectable signal that fires an alert.

## 3. OWASP Top 10 → control · test · detection · phase

| OWASP (2021) | Primary control (where) | Regression test | Detection signal | Closes in |
|---|---|---|---|---|
| **A01** Broken Access Control | DB authorities + owner-scoped reads (`SecurityConfig`, `*Service.findByIdAndUserId`) | `OrderService/Cart/Payment` + `AdminAuthorizationTest` | role-vs-DB mismatch; admin access by non-admin | ✅ + P7 |
| **A02** Crypto/Secrets | BCrypt; HMAC JWT; `JwtSecretValidator`; Gitleaks gate | `JwtServiceTest` | Gitleaks run red | P6 |
| **A03** Injection | Parameterized JPA; Bean Validation | `AuthValidationTest`; CodeQL | SAST sink alert | P4 |
| **A04** Insecure Design | server-side recompute; `@Version`; idempotency key | `CheckoutService/Coupon/Inventory` tests | order total ≠ Σ lines | ✅ + P3 |
| **A05** Misconfig | security headers; per-env CORS; trimmed actuator | `AdminAuthorizationTest`; DAST | missing-header / CORS finding | ✅(headers) + P2 |
| **A06** Vulnerable Components | Trivy SCA + Dependabot + dependency-review | dependency-review gate | new CRITICAL CVE in PR | ✅ + P6 |
| **A07** Auth Failures | lockout, revocation, refresh rotation, MFA | `JwtAuthenticationFilterTest`, lockout tests | failed-login / lockout spike | ✅(partial) + P1 |
| **A08** Integrity Failures | outbox + idempotency + DLT; SBOM; image signing | `OutboxRelay`, `NotificationListener`, `ProcessedEventTracker` tests | DLT growth | ✅(messaging) + P6 |
| **A09** Logging/Monitoring | security-event logging + tracing + audit | (manual) | the alerts in P7 | P7 |
| **A10** SSRF | URL allow-list for server-side fetches | (to add in P4) | outbound to internal IP | P4 |

## 4. Offensive testing with Burp Suite (methodology)

DAST is the layer that attacks the **running** app the way an attacker would. Run it
against the gateway (`http://localhost:8088`) with the full stack up
(`docker compose --profile apps up -d --build`).

**Setup**
1. Proxy traffic through Burp; import the OpenAPI spec (NightVision can extract it)
   into Burp's target site map for full endpoint coverage.
2. Create two accounts (customer A, customer B) and one admin. Configure **session
   handling rules** + a **login macro** so authenticated scans don't drop the JWT.
3. Scope = `/api/**` through the gateway only (don't scan third-party hosts).

**What to test, by category**
- **A01/IDOR:** with A's session, request B's order/cart/payment ids → must be
  `403/404`. Use Burp **Intruder** to fuzz ids; **Autorize** extension to diff
  authenticated-vs-other-user responses automatically.
- **A01 privilege:** hand-edit the JWT `role` claim → must still be `403` on
  `/api/admin/**` (authorities come from the DB).
- **A02 tokens:** strip/alter the signature, try `alg=none` → must be rejected.
- **A03 injection:** Burp **Scanner** active scan on every param + SQL/NoSQL/command
  payloads via Intruder; confirm no error/behavior change.
- **A04 logic:** replay checkout with manipulated body/extra fields; double-submit to
  test idempotency; coupon reuse/stacking.
- **A05 headers/CORS:** confirm the security headers on responses; send a cross-origin
  `Origin` → must be rejected.
- **A07 rate-limit:** Intruder burst on `/api/auth/login` → expect `429` + account
  lockout.

**Close the loop:** run each attack first against the matching `vulnerable-lab`
commit (it succeeds), then against `main` (it fails) — and confirm the **detection
signal** (audit entry / metric / alert) fired. A control that can't be *seen* failing
isn't done.

Burp Suite Pro automates the active scan; the free **OWASP ZAP** baseline
(`zap-baseline.py` against `:8088`) is the no-license fallback already documented in
[`security-testing.md`](security-testing.md) §3.

## 5. Definition of done

Aurora is "hardened to plan" when, for **every** OWASP category: there is a control in
code, a regression test that fails if the control is removed, and a detection signal
that fires when it's attacked — **and** the CI gates enforce it, a scheduled DAST pass
is green, and no P1/P2 item in [`../devops/cicd-security.md`](../devops/cicd-security.md)
remains open. Security is then maintained by the loop, not by memory.
