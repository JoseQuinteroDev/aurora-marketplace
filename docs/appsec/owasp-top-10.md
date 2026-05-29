# OWASP Top 10 (2021) — Coverage Map

This document maps each **OWASP Top 10 2021** risk category to Aurora
Marketplace: what the risk means *for this system*, the **controls already in
code** (with file references), the **status**, and the **remediation** for any
gap. It is the risk-centric companion to the [threat model](threat-model.md) and
the [controls catalog](security-controls.md).

**Status legend:** ✅ Mitigated · ⚠️ Partial / context-dependent · ❌ Open gap (tracked)

| # | Category | Status |
|---|---|---|
| A01 | Broken Access Control | ⚠️ |
| A02 | Cryptographic Failures | ⚠️ |
| A03 | Injection | ✅ |
| A04 | Insecure Design | ✅ |
| A05 | Security Misconfiguration | ⚠️ |
| A06 | Vulnerable & Outdated Components | ⚠️ |
| A07 | Identification & Authentication Failures | ⚠️ |
| A08 | Software & Data Integrity Failures | ✅ |
| A09 | Security Logging & Monitoring Failures | ✅ |
| A10 | Server-Side Request Forgery (SSRF) | ✅ |

---

## A01 — Broken Access Control ⚠️

**What it means here:** a user reaching admin functions, or one customer reading
or modifying another customer's cart, order, or review (IDOR).

**Controls in place**
- URL-level authorization: `/api/admin/**` requires `ROLE_ADMIN`; all non-public
  routes require authentication — `config/SecurityConfig.java` (`authorizeHttpRequests`).
- Authorities are **loaded from the database**, not trusted from the JWT `role`
  claim — `SecurityConfig.userDetailsService`. A tampered claim cannot escalate.
- Stateless sessions (`SessionCreationPolicy.STATELESS`) — no session fixation.
- Default-deny: `anyRequest().authenticated()`.

**Residual / gaps**
- **Object-level ownership (IDOR)** is enforced inside services, not by a blanket
  filter. Every endpoint that takes a resource id must assert the caller owns it.

**Remediation**
- Add ownership-assertion integration tests for every customer-scoped resource
  (cart, order, review, wishlist). Treat IDOR as a first-class `vulnerable-lab`
  scenario. See [`security-testing.md`](security-testing.md).

---

## A02 — Cryptographic Failures ⚠️

**What it means here:** weak password storage, weak/forgeable tokens, or
sensitive data exposed in transit or at rest.

**Controls in place**
- **BCrypt** password hashing with per-password salt — `SecurityConfig.passwordEncoder()`.
- **JWT signed with HMAC-SHA**; verification pins the algorithm to the key via
  `Jwts.parser().verifyWith(key)`, which rejects `alg=none` and algorithm-confusion
  downgrades — `security/jwt/JwtService.java`.
- Signing secret externalized to `APP_SECURITY_JWT_SECRET` (≥32 chars required in
  production, per `CLAUDE.md`); never hard-coded in source.

**Residual / gaps**
- **Transport:** the local compose stack runs Postgres/Redis/Kafka/SMTP in
  plaintext; **TLS in transit** is a deployment responsibility.
- **At rest:** no database/volume encryption locally — infra control.
- **Secret strength** depends on the operator supplying a strong secret; weak
  secrets weaken HMAC. The `.env.example` flags this.

**Remediation**
- Enforce TLS everywhere outside local dev; enable broker SASL/mTLS.
- Inject the JWT secret from a real secret manager; document minimum entropy.

---

## A03 — Injection ✅

**What it means here:** SQL injection, and command/template/log injection.

**Controls in place**
- Data access via **Spring Data JPA / parameterized queries** — no
  string-concatenated SQL anywhere in the domain repositories.
- **Bean Validation** on all request DTOs constrains shape and size before any
  processing (e.g. `auth/dto/RegisterRequest.java`).
- DTO binding (not entity binding) prevents overposting.
- Schema is Flyway-managed with `ddl-auto: validate` — no dynamic DDL.

**Residual / gaps**
- Email content assembled from event data in the notification-service must encode
  fields rather than concatenate into headers (header-injection class).

**Remediation**
- Keep mail strictly template-driven; add a test asserting CRLF in event-derived
  fields cannot inject headers.

---

## A04 — Insecure Design ✅

**What it means here:** flaws that no amount of clean implementation fixes —
trusting the client, dual-write data loss, missing abuse controls by design.

**Controls in place**
- **Server-side recomputation** of prices, totals, discounts, and stock during
  checkout/payment — the platform's core security invariant (*never trust client
  values*). See `checkout`/`payment` services and `02_backend_architecture.md`.
- **Transactional outbox** eliminates the dual-write problem by design: an event
  exists if and only if its business transaction committed
  (`messaging/outbox/`). See `03_event_driven_microservices.md`.
- **Idempotent consumers + DLTs** designed in, because at-least-once delivery
  *will* redeliver.
- **Defense in depth** layered edge → app → data → messaging (see
  [`README.md`](README.md)).
- A documented [threat model](threat-model.md) — design-level risk analysis is
  itself an A04 control.

**Residual / gaps**
- No per-account abuse throttling (distinct from IP rate limiting) — see A07.

---

## A05 — Security Misconfiguration ⚠️

**What it means here:** insecure defaults, overexposed endpoints, verbose errors,
permissive CORS, default credentials.

**Controls in place**
- CSRF, form login, HTTP Basic, and logout are explicitly **disabled** for the
  stateless API — `SecurityConfig` (intentional, not default drift).
- Actuator is **not** wide open: only `health`, `info`, and `prometheus` are
  permitted publicly; everything else sits behind Spring Security.
- `GlobalExceptionHandler` returns clean JSON — **no stack traces** to clients.
- Gateway dedupes CORS headers and orders routes deterministically.

**Residual / gaps**
- **CORS** allows `http://localhost:*` with credentials — fine for dev,
  unacceptable for prod (`gateway/application.yml`).
- **No HTTP security headers** (HSTS, CSP, `X-Content-Type-Options`,
  `X-Frame-Options`, `Referrer-Policy`).
- **Default credentials** for Postgres/MinIO in `docker-compose.yml` (dev only).

**Remediation**
- Per-environment CORS allow-list; remove wildcard origins outside dev.
- Add security-header response filter at the gateway.
- Move compose credentials to `.env` (provided `.env.example`); never reuse dev
  defaults in deployed environments.

---

## A06 — Vulnerable & Outdated Components ⚠️

**What it means here:** known-CVE dependencies in Maven/npm/base images.

**Controls in place**
- Current stack: **Java 21**, **Spring Boot 3.5.14**, **Angular 21** — actively
  maintained, recent majors.
- Spring Boot BOM centralizes and aligns transitive versions.

**Residual / gaps**
- No automated dependency scanning or update flow *yet*.

**Remediation (DevSecOps phase)**
- **SCA** (OWASP Dependency-Check / Trivy) in CI on every PR.
- **Dependabot** for Maven, npm, Docker base images, and GitHub Actions.
- **SBOM** (CycloneDX) generated per build.
- Full design in [`../devops/cicd-security.md`](../devops/cicd-security.md).

---

## A07 — Identification & Authentication Failures ⚠️

**What it means here:** weak login, guessable/forgeable tokens, missing lockout,
poor session/token lifecycle.

**Controls in place**
- BCrypt-verified credentials via `DaoAuthenticationProvider`.
- Signed, expiring JWTs; invalid/expired tokens are rejected and the security
  context is cleared — `JwtAuthenticationFilter`.
- Password policy at registration (min length, bcrypt 72-byte cap respected) —
  `auth/dto/RegisterRequest.java`.
- Disabled accounts cannot authenticate (`enabled` flag in `userDetailsService`).
- IP-based rate limiting at the gateway slows brute force.

**Residual / gaps**
- **No per-account lockout / progressive delay** on repeated failures.
- **No token revocation** — stateless tokens are valid until expiry; logout is
  client-side only.
- **No refresh-token rotation**, email verification, or password-reset flow yet.

**Remediation**
- Add per-account attempt throttling.
- Short access-token TTL + refresh token with a server-side revocation list.
- Implement email verification and password reset with single-use, expiring tokens.

---

## A08 — Software & Data Integrity Failures ✅

**What it means here:** untrusted data/code mutating state, insecure
deserialization, broken event integrity, unverified pipeline artifacts.

**Controls in place**
- **Transactional outbox** guarantees event/state integrity — no phantom or lost
  events; relay claim uses `FOR UPDATE SKIP LOCKED` so concurrent relays don't
  duplicate (`messaging/outbox/`).
- **Idempotent consumption** with `eventId` dedup (`ProcessedEventTracker`) and
  poison-message quarantine to DLTs.
- Events are plain JSON with `@JsonIgnoreProperties(ignoreUnknown = true)` —
  **no polymorphic/Java-native deserialization**, so no gadget-chain exposure.
- Flyway migrations are versioned and validated — schema changes are auditable.

**Residual / gaps**
- CI artifact integrity (signing, provenance) is not yet established.

**Remediation (DevSecOps phase)**
- Generate SBOMs; sign container images; pin GitHub Actions by SHA.

---

## A09 — Security Logging & Monitoring Failures ✅

**What it means here:** attacks invisible because nothing is recorded or watched.

**Controls in place**
- **Audit log** of sensitive business events — ORDER_CREATED,
  ORDER_STATUS_CHANGED, PAYMENT_SIMULATED_*, COUPON_USED — with admin read access
  (`audit` domain, `GET /api/admin/audit-logs`).
- **Prometheus metrics** on all three services (`/actuator/prometheus`).
- **Distributed tracing**: `traceId`/`spanId` correlation in every log line via
  Micrometer Tracing/Brave — a request is traceable across gateway → core →
  consumer.
- Auth failures clear context predictably; gateway logs throttling/circuit events.

**Residual / gaps**
- No centralized log aggregation or alerting rules shipped with the repo.

**Remediation**
- Ship example alerting rules (auth-failure spikes, DLT growth, circuit-open)
  and document a log-aggregation target in `cicd-security.md`/ops docs.

---

## A10 — Server-Side Request Forgery (SSRF) ✅

**What it means here:** the server fetching attacker-controlled URLs.

**Assessment**
- The core does **not** fetch user-supplied URLs. Outbound calls are to **fixed,
  configured** dependencies only: PostgreSQL, Kafka, and (notification-service)
  the configured SMTP host. There is no image-by-URL fetch, webhook callback, or
  link-preview feature.

**Residual / gaps**
- Low. The risk would reappear if a future feature fetches remote resources
  (e.g. avatar-by-URL, webhooks).

**Remediation (preventive)**
- If such a feature is added: allow-list destinations, block private/link-local
  IP ranges, disable redirects, and isolate egress.

---

## Summary & priority backlog

| Risk | Status | Top remediation |
|---|---|---|
| A01 Access Control | ⚠️ | IDOR ownership tests on every resource (P1) |
| A07 AuthN | ⚠️ | Token revocation + account lockout (P1/P2) |
| A05 Misconfig | ⚠️ | Security headers + per-env CORS (P2) |
| A06 Components | ⚠️ | SCA + Dependabot + SBOM in CI (P2) |
| A02 Crypto | ⚠️ | TLS in transit + secret manager (P3, infra) |
| A03/A04/A08/A09/A10 | ✅ | Maintain; add tests to prevent regression |

This map is reassessed whenever a new endpoint, dependency upgrade, or external
integration lands.
