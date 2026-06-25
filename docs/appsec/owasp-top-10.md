# OWASP Top 10 (2021) — Coverage Map

This document maps each **OWASP Top 10 2021** risk category to Aurora
Marketplace: what the risk means *for this system*, the **controls already in
code** (with file references), the **status**, and the **remediation** for any
gap. It is the risk-centric companion to the [threat model](threat-model.md) and
the [controls catalog](security-controls.md).

> **How this map was validated.** Beyond manual review, the statuses below were
> re-derived by a code-grounded audit (one pass per category against the actual
> source) and then an adversarial verification pass that tried to *falsify* each
> claimed control and gap. Where the two disagreed, the skeptical reading won.
> That process downgraded A04/A08/A09 from a previous optimistic ✅ and upgraded
> A06 once the now-shipped CI scanning was credited. The result is the honest
> current state — green where a control demonstrably holds in code, amber where
> it is partial, red where it is an open gap. Gaps are tracked, not hidden.

> **Remediation pass (2026-06-20).** Several HIGH findings below have since been
> fixed in code — JWT-secret fail-fast, gateway-actuator lock-down, per-account
> lockout, token revocation / server-side logout, and security logging. Those
> items are marked **✅ Remediated** inline. A category's status stays ⚠️ where
> other, lower-severity gaps remain.

**Status legend:** ✅ Mitigated · ⚠️ Partial / context-dependent · ❌ Open gap (tracked)

| # | Category | Status |
|---|---|---|
| A01 | Broken Access Control | ⚠️ |
| A02 | Cryptographic Failures | ⚠️ |
| A03 | Injection | ✅ |
| A04 | Insecure Design | ⚠️ |
| A05 | Security Misconfiguration | ⚠️ |
| A06 | Vulnerable & Outdated Components | ✅ |
| A07 | Identification & Authentication Failures | ⚠️ |
| A08 | Software & Data Integrity Failures | ⚠️ |
| A09 | Security Logging & Monitoring Failures | ⚠️ |
| A10 | Server-Side Request Forgery (SSRF) | ✅ |

---

## A01 — Broken Access Control ⚠️

**What it means here:** a user reaching admin functions, or one customer reading
or modifying another customer's cart, order, or payment (IDOR).

**Controls in place**
- **Default-deny URL authorization**: `/api/admin/**` requires `ROLE_ADMIN`, a
  small explicit allow-list is public (auth register/login, catalog GETs,
  actuator health/info/prometheus), and `anyRequest().authenticated()` denies
  everything else — `config/SecurityConfig.java:53-60`.
- **Every admin surface is namespaced** under `/api/admin/**`, so the single
  `ROLE_ADMIN` rule covers all 10 admin controllers (orders, products,
  categories, brands, coupons, inventory, reviews, audit-logs, dashboard, batch).
- **The JWT `role` claim is never trusted for authorization.** The filter takes
  only the signature-verified *subject* (email) from the token, then loads
  authorities **from the database** via `userDetailsService` —
  `security/jwt/JwtAuthenticationFilter.java:58-77` + `SecurityConfig.java:86-97`.
  A tampered/stale `role` claim cannot escalate.
- **Object-level ownership (anti-IDOR)** is enforced consistently with
  current-user-scoped repository queries: orders via `findByIdAndUserId`
  (`OrderService.java:38`), cart items via `findByIdAndCartUserId`
  (`CartService.java:86,96`), payment via `findByIdAndUserId`
  (`PaymentService.java:54`), checkout via `findByUserId`, wishlist via
  user-id-scoped queries. The user always comes from the authenticated principal
  (`CurrentUserService.java:27`), never from the request body.
- **No privilege escalation via self-registration**: `RegisterRequest` has no
  `role` field and `AuthService.register()` hardcodes `Role.CUSTOMER`.
- Stateless sessions (no fixation surface); custom 401/403 handlers return clean
  JSON without leaking data on forced browsing — `SecurityConfig.java:48-80`.

**Residual / gaps**
- **No IDOR regression guard (P1).** Ownership lives entirely in hand-written
  scoped queries — there is no `@PreAuthorize`/`@EnableMethodSecurity` and no
  integration test asserting customer A gets `404`/`403` on customer B's
  order/cart-item/payment/wishlist. The control is correct today but
  *structurally fragile*: a future endpoint added with a plain `findById` would
  silently reintroduce IDOR with nothing to catch it.
- **Account deactivation does not revoke an issued token.** `.disabled(!enabled)`
  (`SecurityConfig.java:94`) is only checked by the login provider, **not** on the
  JWT request path (`JwtAuthenticationFilter` never consults `isEnabled()`). With
  no token revocation (see A07), a user disabled mid-session stays authenticated
  until the token expires.
- **CORS** allows `http://localhost:*` *with credentials* — fine for dev,
  unacceptable for prod (`gateway/application.yml`).
- *(Informational)* `DevTestController` (`/api/dev`) is authenticated-only and
  `@Profile("dev")`, exposing only echo/validation responses (no data, not loaded
  in prod).

**Remediation**
- Add ownership-assertion integration tests for every customer-scoped resource
  (the single highest-value test class to add — see
  [`security-testing.md`](security-testing.md)); optionally a thin
  ownership helper or ArchUnit rule so new id-bearing endpoints fail closed.
- Check `isEnabled()` on the JWT path (or pair with token revocation) so disabling
  an account takes effect immediately.

---

## A02 — Cryptographic Failures ⚠️

**What it means here:** weak password storage, weak/forgeable tokens, or
sensitive data exposed in transit or at rest.

**Controls in place**
- **BCrypt** password hashing with per-password salt — `SecurityConfig.java:116-118`;
  hash applied before persistence (`AuthService.java:57`) and never returned in
  any DTO (`AuthUserResponse` has no hash field).
- **JWT signed with HMAC-SHA**; verification pins the algorithm to the key via
  `parser().verifyWith(signingKey()).parseSignedClaims(...)`, which rejects
  `alg=none` and algorithm-confusion downgrades — `security/jwt/JwtService.java:58-69`.
  Tamper/forge/expiry rejection is covered by unit tests (`JwtServiceTest`).
- Signing secret externalized to `APP_SECURITY_JWT_SECRET` and validated
  non-blank + `@Size(min=32)` at startup — `security/jwt/JwtProperties.java:13-15`.
- No PAN/card data is stored (payments are simulated); no sensitive-data logging
  was found.

**Residual / gaps**
- **⛔ HIGH — a usable default signing secret is committed in source.**
  `application.yml:79` ships
  `aurora-marketplace-development-secret-change-me-before-production-1234567890`
  and `docker-compose.yml:145` ships a *different* fallback
  `dev-only-insecure-jwt-secret-change-me-32`. Both are **long enough to pass the
  `@Size(min=32)` gate**, so a deployment that forgets to set
  `APP_SECURITY_JWT_SECRET` silently runs on a **publicly known HMAC key** — and
  anyone with the repo can then forge a valid admin JWT. The length check gives
  false assurance.
  **✅ Remediated:** `security/jwt/JwtSecretValidator` now refuses to start under
  the `prod` profile when the secret is blank or a known placeholder, and warns
  loudly in every other environment. Providing a real secret (ideally from a
  secret manager) is still required.
- **No TLS on any data-in-transit channel.** JDBC has no `sslmode`, Kafka uses
  `PLAINTEXT`, Redis is unauthenticated, SMTP disables AUTH/STARTTLS — confidentiality
  in transit is wholly deferred to the deployment, with nothing in-repo enforcing it.
- **JWT stored in browser `localStorage`** (`frontend/.../auth.service.ts`),
  readable by any script — a single XSS becomes full token theft, and the token
  can't be revoked before expiry.
- **No encryption at rest**; combined with default compose credentials, at-rest
  PII is unprotected if defaults reach a deployed environment.

**Remediation**
- **Do not ship a usable default secret.** Fail fast outside the `dev` profile if
  `APP_SECURITY_JWT_SECRET` is unset or equals a known placeholder; source it from
  a secret manager; consider raising the minimum to 64 bytes (HS512-grade).
- Require TLS everywhere outside local dev (`sslmode=require`, SASL_SSL/mTLS for
  Kafka, SMTP STARTTLS); terminate HTTPS at/before the gateway.
- Prefer an `HttpOnly`/`Secure`/`SameSite` cookie for the token (with CSRF
  defense) or pair short TTL + revocation with a strict CSP.

---

## A03 — Injection ✅

**What it means here:** SQL injection, and command/template/log injection.

**Controls in place**
- **Zero raw SQL.** No `EntityManager`/`createNativeQuery`/`JdbcTemplate`/
  `Criteria`/`Specification` anywhere — 100% Spring Data JPA. Every `@Query` is
  JPQL with `@Param`-bound parameters, including the only user-input-driven query
  (product search), which uses a **bound** `LIKE`:
  `lower(product.name) like lower(concat('%', :query, '%'))` —
  `catalog/product/repository/ProductRepository.java:27-37`.
- **Pervasive Bean Validation** (95 constraints across 28 DTOs/controllers, wired
  with `@Valid @RequestBody`); requests bind to immutable DTO **records**, not
  entities (no overposting).
- **No expression/template/OS-command evaluation** — no SpEL parser, ScriptEngine,
  `Runtime.exec`/`ProcessBuilder`, or template engine present.
- Logging is parameterized SLF4J (`{}` placeholders) with internal values — no
  log-injection sink. Email is built via Spring's structured `SimpleMailMessage`
  API, not manual header concatenation (mitigates CRLF header injection).
- Batch CSV import parses into typed DTOs and persists only via JPA — CSV content
  never reaches a SQL string; file paths come from server config, not requests.
- `GlobalExceptionHandler` returns generic JSON — no SQL/stack-trace leakage.

**Residual / gaps (all low, hardening)**
- The email CRLF-injection guarantee is untested — add a negative test feeding
  `\r\nBcc:` into event-derived recipient/subject.
- Product-search `LIKE` doesn't escape `%`/`_` wildcards — a query-semantics
  nuisance, **not** SQL injection.
- `BatchFileReader` uses naive `line.split(",")` (no quoted-field handling) — a
  data-quality robustness gap, not an injection vector.

---

## A04 — Insecure Design ⚠️

**What it means here:** flaws no clean implementation fixes — trusting the client,
dual-write data loss, missing concurrency/idempotency controls by design.

**Controls in place**
- **Server-side recomputation** of subtotal, discount, total and stock from
  DB-resident prices during checkout/payment — the platform's core invariant
  (*never trust client values*). Cart DTOs carry only `variantId` + `quantity`,
  never a price (`CheckoutService.java:95-100`, `CartService.java`).
- **Stock validation + DB backstop**: stock re-checked and decremented at confirm,
  with a `CHECK (available_quantity >= 0)` constraint that **fails safe** under a
  losing concurrent decrement — `V2__...sql`.
- **Coupon re-validated** at checkout (expiry/active/min-order/max-uses/per-user),
  capped at 100% and floored at zero (`promotion/service/CouponService.java`).
- **Payment amount** taken from the server-side order total; state guards reject
  paying cancelled/refunded or re-paying a PAID order (`PaymentService.java:57-75`).
- **Transactional Outbox** eliminates dual-write (event exists iff the tx
  committed); relay claim uses `FOR UPDATE SKIP LOCKED`; consumers are idempotent
  with per-topic DLTs — `messaging/outbox/`, see
  [`03_event_driven_microservices.md`](../architecture/03_event_driven_microservices.md).
- A documented [threat model](threat-model.md) is itself an A04 control.

**Residual / gaps**
- **No optimistic (`@Version`) or pessimistic locking in the commerce domain.**
  Stock decrement is a read-then-write race (`CheckoutService.java:204-213`); the
  DB CHECK prevents oversell but only by rolling back the loser's whole checkout
  rather than returning a clean business error (medium).
- **Coupon usage limit is a TOCTOU race with no DB backstop** — `coupon_usages`
  has no `UNIQUE(coupon_id,user_id)` and validation is count-then-insert, so
  concurrent checkouts can exceed `maxUses`/`maxUsesPerUser` (medium).
- **Checkout has no idempotency key** — a double-submit/retry of
  `POST /api/checkout/confirm` creates two orders, double-decrements stock, and
  emits two `ORDER_CREATED` events (medium).
- Consumer dedup is **in-memory only** (lost on restart, not shared across
  instances) — a redelivery after restart re-sends the email (low).
- Admin order-status update applies any status with **no state-machine
  validation** (e.g. `DELIVERED → PENDING_PAYMENT`) (low).
- **No per-account abuse throttling** distinct from the gateway's per-IP limiter
  (low) — see A07.

**Remediation**
- Add `@Version` to `Inventory`/`Order` (or use a conditional `UPDATE ... WHERE
  available_quantity >= :q`) to serialize stock/payment transitions cleanly.
- Add `UNIQUE(coupon_id,user_id)` and/or a locked usage counter for coupons.
- Accept an `Idempotency-Key` on checkout/payment, persisted with a unique
  constraint; persist consumer dedup (DB unique on `event_id` or Redis TTL).
- Define an allowed-transition map for `OrderStatus`.

---

## A05 — Security Misconfiguration ⚠️

**What it means here:** insecure defaults, overexposed endpoints, verbose errors,
permissive CORS, default credentials.

**Controls in place**
- Hibernate `ddl-auto: validate` with Flyway-managed schema — no auto-DDL.
- **Core** actuator exposure is a narrow allow-list and sits behind Spring
  Security (only `health`/`info`/`prometheus` permitted; `metrics` and the rest
  require auth) — `application.yml:50-54` + `SecurityConfig.java:54-59`.
- Stateful-web defaults explicitly disabled (CSRF/formLogin/httpBasic/logout),
  `STATELESS` policy — `SecurityConfig.java:48-52`.
- **No stack traces to clients**: `ErrorResponse` has no stacktrace field; the
  catch-all returns a fixed generic message — `GlobalExceptionHandler.java:114-122`.
- **Dockerfile hardening**: multi-stage, slim JRE base, **non-root** user
  (uid/gid 1001), `--no-install-recommends`, apt lists removed (all three images);
  `.dockerignore` excludes build output/VCS.
- Spring Batch auto-run disabled; no Spring default in-memory user.

**Residual / gaps**
- **✅ Remediated (HIGH) — gateway actuator exposure trimmed.** The gateway no
  longer exposes the `gateway` (route-topology) or `metrics` endpoints; only
  `health,info,prometheus` remain on `:8088` (`gateway/application.yml`). Putting
  reactive Spring Security in front of the rest is the further hardening step.
- **✅ Remediated (HIGH) — default JWT secret fail-fast** under the `prod` profile
  (`JwtSecretValidator`); see A02. DB/MinIO compose defaults remain a deploy-time concern.
- **✅ Remediated (MEDIUM) — HTTP security headers on the core.** `SecurityConfig`
  now sets CSP (`default-src 'none'; frame-ancestors 'none'; base-uri 'none';
  form-action 'none'`), `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff`, `Permissions-Policy` and HSTS. Mirroring them
  in a gateway-edge response filter (so non-core/error paths are covered too)
  remains a follow-up.
- **CORS** is permissive and hardcoded (`http://localhost:*` + `allow-credentials:
  true`, no per-env allow-list) (medium).
- `management.endpoint.health.show-details: always` on all three services exposes
  internal component health to any caller (low).
- `org.hibernate.SQL: DEBUG` + `format_sql: true` on by default (low); Kafka
  `auto-create-topics` + PLAINTEXT and an unauthenticated kafka-ui (low, dev infra).

**Remediation**
- Add Spring Security (reactive) to the gateway and bind management to a separate,
  non-routable port — or at least drop `gateway`/`metrics` from public exposure.
- ✅ Security headers are set on the core (`SecurityConfig`); the remaining step is
  to **mirror them in a gateway-edge response filter** so non-core and error
  paths carry them too. *(The DAST workflow flags missing headers automatically.)*
- Per-environment CORS allow-list; fail fast on default secrets; profile-specific
  logging; a hardened compose overlay for non-local use.

---

## A06 — Vulnerable & Outdated Components ✅

**What it means here:** known-CVE dependencies in Maven/npm/base images.

**Controls in place**
- Current, actively-maintained stack: **Spring Boot 3.5.14** parent BOM across all
  three Java modules, **Spring Cloud 2025.0.0** (gateway), **jjwt 0.13.0** pinned,
  **Angular 21 / TypeScript 5.9** on the frontend. The BOM centralizes transitive
  versions.
- **SCA in CI**: Trivy filesystem vuln scan (Maven + npm) on push/PR + weekly,
  SARIF to the Security tab, DB repository pinned to avoid rate-limit failures —
  `security.yml` (`dependency-scan`).
- **Dependabot** across every ecosystem: Maven ×3, npm, Docker base images ×3,
  GitHub Actions; security updates raised regardless of schedule —
  `.github/dependabot.yml`.
- **CycloneDX SBOM** generated and published each run — `security.yml` (`sbom`).
- **IaC/image** misconfig scanning: Trivy config + Hadolint on all three
  Dockerfiles; CodeQL SAST complements component scanning.

> This category was previously rated ⚠️ on the assumption SCA/Dependabot/SBOM were
> future work. They are now implemented in CI, so the honest rating is ✅. See
> [`../devops/cicd-security.md`](../devops/cicd-security.md).

**Residual / gaps (hardening)**
- **GitHub Actions are pinned by mutable tag, not commit SHA** (medium) — the
  pipeline that defends the supply chain is itself tag-mutable. Pin every `uses:`
  to a 40-char SHA (Dependabot keeps them current). *Especially* `anchore/sbom-action@v0`
  (floating major).
- Docker base images use floating tags with no `@sha256` digest pin (low) —
  builds aren't reproducible and a re-pushed tag silently changes the runtime.
- SBOM is a transient CI artifact only — not built at Maven time nor attached/
  attested to images (low).
- Trivy SCA is report-only — a new CRITICAL dependency doesn't fail CI (low; see
  the SCA-gating recommendation in the DevSecOps doc).

---

## A07 — Identification & Authentication Failures ⚠️

**What it means here:** weak login, guessable/forgeable tokens, missing lockout,
poor session/token lifecycle.

**Controls in place**
- BCrypt-verified credentials via `DaoAuthenticationProvider`; authorities loaded
  from the DB (a forged `role` claim can't escalate); disabled accounts blocked
  *at login*; stateless sessions.
- Signed, expiring JWTs with the verification algorithm pinned via `verifyWith`;
  invalid/expired tokens reject and clear the context (`JwtAuthenticationFilter`).
- Generic `INVALID_CREDENTIALS` on both bad-password and unknown-user paths — **no
  user enumeration** (`AuthService.java:76-89`); email normalized to lowercase to
  prevent duplicate-account aliasing.
- Registration enforces password length (`@Size(min=8,max=72)`); IP-based rate
  limiting at the gateway slows brute force.
- **✅ Remediated — per-account lockout, enforced core-side.** `LoginAttemptService`
  counts consecutive failures and locks the account for a cool-down window
  (5 attempts / 15 min by default, configurable). Because it lives in the core
  service (not the gateway), it holds even when the gateway is bypassed — closing
  both the "no lockout" and "rate-limit bypassable" gaps. Lockouts are audited
  (`ACCOUNT_LOCKED`); the accounting commits in its own `REQUIRES_NEW` transaction
  so a failed login still records (`V7__add_user_login_lockout.sql`, `User`).
- **✅ Remediated — token revocation + server-side logout.** Tokens now carry a
  `jti`; `POST /api/auth/logout` revokes the current token via a denylist
  (`TokenDenylistService` / `token_denylist`) that the auth filter checks on every
  request, so a logged-out or stolen token stops working before it expires.

**Residual / gaps**
- No refresh-token rotation (medium); no credential-stuffing/breached-password/MFA
  defense (medium); token in `localStorage` rather than an `HttpOnly` cookie
  (medium); no email verification or password reset (low); length-only password
  policy accepts weak-but-long passwords (low).

**Remediation (remaining)**
- Add rotating refresh tokens (short access-token TTL + reuse detection) on top of
  the now-shipped `jti` revocation denylist.
- Breached-password check (HIBP k-anonymity) at registration/change; optional TOTP
  MFA for admins; move the token to an `HttpOnly`/`Secure`/`SameSite` cookie.

---

## A08 — Software & Data Integrity Failures ⚠️

**What it means here:** untrusted data/code mutating state, insecure
deserialization, broken event integrity, unverified pipeline artifacts.

**Controls in place (the data-integrity half is excellent)**
- **Transactional Outbox** — event row written via JPA inside the business tx, so
  an event exists iff the change committed; relay claims with `FOR UPDATE SKIP
  LOCKED`; rows park `FAILED` after max attempts, never lost — `messaging/outbox/`.
- **Idempotent consumption** — `eventId` dedup, marked processed only *after*
  successful delivery; per-topic DLTs + exponential backoff; poison messages
  dead-lettered immediately via `NonRetryableEventException`.
- **No unsafe deserialization** — Kafka payloads are plain JSON over
  String(De)serializer parsed into immutable Jackson records with
  `@JsonIgnoreProperties(ignoreUnknown = true)`; no `ObjectInputStream`/
  `enableDefaultTyping`/`XMLDecoder` anywhere.
- Server-generated event identity (UUID/timestamp set in the producer factory).
- Supply-chain hygiene well above portfolio norm: SBOM, Dependabot, Trivy SCA,
  Gitleaks hard gate, CodeQL; least-privilege CI token; non-root containers.

**Residual / gaps**
- **GitHub Actions pinned by mutable tag, not commit SHA** (medium) — a re-tagged
  third-party action would run attacker code in the very pipeline meant to protect
  the supply chain.
- **No image signing / provenance** (no cosign/SLSA attestation) — a deployed image
  can't be cryptographically verified as the genuine CI output (medium).
- The Maven wrapper download is not checksum-verified
  (`distributionSha256Sum` absent) (low).
- Consumer dedup is in-memory only (low — worst case a duplicate email).
- Trivy SCA / frontend tests are non-blocking (low; documented gating choice).

> Previously ✅; downgraded to ⚠️ because two of the project's own remediation
> items (SHA-pinning, image signing) remain genuinely unimplemented.

**Remediation**
- Pin every `uses:` to a full commit SHA (Dependabot keeps them current); add
  cosign signing + build-provenance attestation; add `distributionSha256Sum` to
  each `maven-wrapper.properties`.

---

## A09 — Security Logging & Monitoring Failures ⚠️

**What it means here:** attacks invisible because nothing is recorded or watched.

**Controls in place**
- **Transactional business-event audit trail** — actor + event type + entity +
  timestamp, written in the *same* DB transaction as the action, admin-only read
  (`audit/service/AuditLogService.java`, `GET /api/admin/audit-logs`). Covers
  `ORDER_CREATED`, `ORDER_STATUS_CHANGED`, `PAYMENT_SIMULATED_*`, `COUPON_USED`.
- **Batch job audit** (start/finish/status/failure) — `batch_job_audit`.
- **Distributed tracing** — `traceId`/`spanId` correlation in every log line across
  all three services (Micrometer Tracing + Brave); Prometheus metrics on all three.
- notification-service logs delivery, duplicate skips and DLT routing; the outbox
  relay logs exhausted events at ERROR.
- **✅ Remediated — security-event logging.** Authentication outcomes (login
  success/failure, registration), JWT validation failures, and 401/403 authorization
  denials are now logged (`AuthService`, `JwtAuthenticationFilter`, `SecurityConfig`);
  unexpected 500s are logged with their stack trace (`GlobalExceptionHandler`); and
  account lockouts/logouts are written to the audit log (`ACCOUNT_LOCKED`, `LOGOUT`).
  All carry the `traceId`/`spanId` from the MDC pattern.

**Residual / gaps**
- *(✅ Remediated — the two HIGH items here, "no auth-event logging" and "500s
  swallowed without logging", are fixed; see the control above.)*
- Privileged **admin CRUD is not audited** (catalog/coupon/inventory/role changes)
  — "who changed this price/stock?" is unanswerable (medium).
- **Authorization denials (401/403) are not logged** — probing of `/api/admin/**`
  by non-admins is invisible (medium). JWT validation failures clear context
  silently (medium).
- **No alerting/detection rules** ship with the repo — metrics are exposed but
  nothing watches them (medium). The audit log has no integrity/tamper-evidence or
  retention policy (low); no request access log (low).

**Remediation**
- Add a logger + `log.error(msg, ex)` to the catch-all handler; log every auth
  outcome (`AUTH_LOGIN_SUCCESS/FAILURE/REGISTER`) with username + client IP (never
  the password) and persist failures to the audit log.
- Audit admin write operations; log 401/403 denials at WARN with principal/URI/IP.
- Ship example Prometheus alerting rules (auth-failure rate, `*.DLT` growth,
  circuit-open, 5xx rate) and document a log-aggregation/SIEM target.

> A previous ✅ overstated this. The doc also previously claimed "gateway logs
> throttling/circuit events" — the gateway has **no logging code**, so only
> framework defaults apply. Corrected here.

---

## A10 — Server-Side Request Forgery (SSRF) ✅

**What it means here:** the server fetching attacker-controlled URLs.

**Assessment**
- **No server-side outbound HTTP/URL-fetch client exists** in any module
  (repo-wide grep for `RestTemplate`/`WebClient`/`HttpClient`/`HttpURLConnection`/
  `openConnection`/`new URL(`/Feign/OkHttp → zero matches). There is no
  image-by-URL fetch, link preview, webhook, or remote-spec fetch.
- The admin-supplied product image `url` is stored as an **inert string** and
  rendered client-side as `<img src>` — never fetched server-side.
- The only application egress is SMTP to a **fixed, configured** host
  (`SimpleMailMessage`, plain text, no remote-resource embedding). The gateway
  proxies only to **env-pinned static upstreams** (not an open proxy). MinIO is
  provisioned-but-unused (no SDK client in code). Batch jobs read **local** files
  from operator-configured paths.

**Residual / gaps**
- **Latent only** (low): the surface is zero because no remote-fetch feature exists
  yet, and there is no reusable SSRF-safe fetch guardrail. Any future feature that
  fetches a stored/user URL (thumbnailing, import-by-URL, webhooks) would start from
  zero protection.

**Remediation (preventive)**
- Before adding any server-side remote fetch: HTTPS-only + host allow-list, block
  private/loopback/link-local/metadata IPs **after DNS resolution**, disable
  redirect following, tight timeouts, egress-restricted network, and a regression
  test asserting internal IPs are blocked.

---

## Summary & priority backlog

| Risk | Status | Note |
|---|---|---|
| A02/A05 default secret | ✅ Remediated | Fail-fast under `prod` (`JwtSecretValidator`); still set a real secret in deploys |
| A05 gateway actuator | ✅ Remediated | `gateway`/`metrics` endpoints no longer publicly exposed |
| A07 lockout + revocation | ✅ Remediated | Per-account lockout (core-side) + token revocation + `/api/auth/logout` shipped |
| A09 auth/500 logging | ✅ Remediated | Auth events, 500s, authz denials, JWT failures logged; lockout/logout audited |
| A01 Access Control | ⚠️ | IDOR ownership integration tests on every resource — **P1** |
| A05 Misconfig | ⚠️ | Core security headers ✅; gateway-edge header filter + per-env CORS — **P2** |
| A04 Insecure Design | ⚠️ | Locking/`@Version` + coupon unique constraint + checkout idempotency key — **P2** |
| A08 Integrity | ⚠️ | SHA-pin Actions + image signing/provenance — **P2** |
| A07 AuthN (remaining) | ⚠️ | Refresh-token rotation, breached-password/MFA, cookie storage — **P3** |
| A02 Crypto | ⚠️ | TLS in transit + secret manager — **P3 (infra)** |
| A03 / A06 / A10 | ✅ | Maintain; add regression tests; pin image digests / SHA |

**Dynamic coverage:** the [DAST workflow](../../.github/workflows/dast.yml)
(NightVision) exercises the running API and surfaces several of the above at the
edge — missing security headers (A05), CORS behavior (A05/A01), error handling
(A09), and authentication edge cases (A07) — see
[`security-testing.md`](security-testing.md).

This map is reassessed whenever a new endpoint, dependency upgrade, or external
integration lands.
