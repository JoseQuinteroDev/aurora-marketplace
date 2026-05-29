# Threat Model — Aurora Marketplace

This is a **STRIDE** threat model for the Aurora Marketplace platform. It
enumerates assets, draws the trust boundaries, walks the data flows, and for each
component lists the threats (Spoofing, Tampering, Repudiation, Information
disclosure, Denial of service, Elevation of privilege) together with the
mitigations that exist in code — plus the residual risk and what is still open.

- **Scope:** the running platform — Angular SPA, API gateway, commerce core,
  notification-service, and the backing stores (PostgreSQL, Kafka, Redis, object
  storage, SMTP sink).
- **Method:** STRIDE per element, anchored to the data-flow diagram below.
- **Audience:** engineers extending the platform and reviewers assessing its
  security design.

Read alongside [`owasp-top-10.md`](owasp-top-10.md) (risk-centric view) and
[`security-controls.md`](security-controls.md) (control-centric view).

---

## 1. Assets

What an attacker would want, ranked roughly by impact.

| # | Asset | Why it matters |
|---|---|---|
| A1 | **User credentials & password hashes** | Account takeover, credential reuse against other sites. |
| A2 | **JWT signing secret** (`APP_SECURITY_JWT_SECRET`) | Forge any token → impersonate any user/admin. |
| A3 | **Authorization state** (roles, ownership) | Privilege escalation, accessing others' carts/orders. |
| A4 | **Order, payment & pricing integrity** | Financial fraud: pay less, get more, abuse coupons. |
| A5 | **PII** (emails, names, order/shipping data) | Privacy breach, regulatory exposure. |
| A6 | **Inventory & catalog integrity** | Business disruption, oversell, price manipulation. |
| A7 | **Audit log integrity** | Repudiation; hides attacker actions. |
| A8 | **Event stream** (Kafka topics) | Forged/duplicated business events → spurious emails, state drift. |
| A9 | **Service availability** | Lost revenue, reputational damage. |
| A10 | **Infrastructure credentials** (DB, Redis, object store, SMTP) | Full data-plane compromise. |

## 2. Trust boundaries

```
   ┌──────────────────────── UNTRUSTED: public internet ────────────────────────┐
   │  Browser / Angular SPA  —  fully attacker-controllable (client code, tokens) │
   └───────────────────────────────────────┬─────────────────────────────────────┘
                                            │  (TB1) HTTPS edge
   ┌────────────────────────────────────────▼────────────────────────────────────┐
   │  SEMI-TRUSTED EDGE: API Gateway (8088)                                        │
   │  Terminates client traffic, applies CORS / rate limit / circuit breaking.     │
   │  Does NOT authenticate — forwards Bearer tokens downstream.                    │
   └───────────────────────────────────────┬─────────────────────────────────────┘
                                            │  (TB2) internal network
   ┌────────────────────────────────────────▼────────────────────────────────────┐
   │  TRUSTED APP TIER                                                             │
   │  Core (8080): authN + authZ + business rules     notification-svc (8082)     │
   └──────┬───────────────────────────┬────────────────────────┬──────────────────┘
          │ (TB3) JDBC                │ (TB4) Kafka protocol   │ (TB5) SMTP
   ┌──────▼──────┐            ┌────────▼────────┐       ┌───────▼───────┐
   │ PostgreSQL  │            │  Kafka broker   │       │ Mailpit/SMTP  │
   │ (data tier) │            │  (event tier)   │       └───────────────┘
   └─────────────┘            └─────────────────┘   + Redis (rate-limit state), object store
```

| Boundary | Crossing | Primary control |
|---|---|---|
| **TB1** | Internet → Gateway | TLS (deployment), CORS allow-list, per-IP rate limit |
| **TB2** | Gateway → Core/Notification | **Authentication & authorization happen here** (token signature + DB-loaded authorities) |
| **TB3** | Core → PostgreSQL | Credentialed JDBC, parameterized queries (JPA), least-privilege DB user |
| **TB4** | Core/Notification → Kafka | Outbox-gated publish; idempotent consumers; DLTs |
| **TB5** | Notification → SMTP | Internal-only; outbound mail |

**Key design decision:** the gateway is *not* a policy enforcement point for
authentication. It forwards the `Authorization` header unchanged and the **core
service is the trust anchor** — it verifies the JWT signature and, crucially,
**loads the user's authorities from the database** rather than trusting the
token's `role` claim (`SecurityConfig.userDetailsService`). This means a tampered
`role` claim cannot escalate privilege even if it slipped past the gateway.

## 3. Data flows (security-relevant)

1. **Login** — SPA `POST /api/auth/login` → gateway → core. Core authenticates
   via `DaoAuthenticationProvider` + BCrypt, issues a signed JWT
   (`JwtService.generateToken`). Token returned to SPA, stored client-side.
2. **Authenticated request** — SPA sends `Authorization: Bearer <jwt>`. Gateway
   rate-limits per IP and forwards. `JwtAuthenticationFilter` extracts the
   subject, reloads the user from the DB, verifies signature + expiry, and sets
   the security context with **DB-sourced authorities**.
3. **Checkout** — Core recomputes line prices, totals, discounts and stock
   server-side, records an order + audit log, and writes an
   `aurora.orders.created` row to the **outbox in the same transaction**.
4. **Event delivery** — `OutboxRelay` drains PENDING rows to Kafka. The
   notification-service consumes, **dedupes on `eventId`**, sends mail via SMTP,
   and quarantines poison messages to a `<topic>.DLT`.

## 4. STRIDE by component

Legend: ✅ mitigated · ⚠️ partial · ❌ open gap.

### 4.1 Angular SPA (untrusted client)

| STRIDE | Threat | Status | Mitigation / note |
|---|---|---|---|
| S | Forged requests from a malicious page | ✅ | Stateless Bearer auth (no ambient cookies) makes classic CSRF inapplicable; CORS allow-list at the gateway. |
| T | Tampering with prices/totals/quantities in the request body | ✅ | Server **recomputes** every monetary and stock value (`checkout`/`payment` services); client values are inputs, not facts. |
| I | Token theft via XSS | ⚠️ | Validation everywhere; Angular escapes by default. Residual: tokens in browser storage are reachable by XSS — mitigated in depth by short token lifetime; CSP is a tracked gap. |
| E | Editing the JWT `role` claim to become admin | ✅ | Authorities are **reloaded from the DB**, not read from the claim; signature verification rejects unsigned/edited tokens. |

**Client is fully untrusted.** Nothing enforced only in the SPA is a security
control — it is UX. Every rule is re-checked server-side.

### 4.2 API Gateway (semi-trusted edge)

| STRIDE | Threat | Status | Mitigation / note |
|---|---|---|---|
| S | Origin spoofing / unwanted cross-origin use | ⚠️ | CORS allow-list (`gateway/application.yml`) — currently `localhost` only; must be set per environment. |
| T | Header/response tampering | ✅ | `DedupeResponseHeader` for CORS; routes are order-specific to avoid mis-routing. |
| D | Volumetric abuse / brute force | ✅ | Redis token-bucket rate limit per client IP (`RequestRateLimiter` + `clientIpKeyResolver`); circuit breakers + timeouts shed load when the core is unhealthy. |
| I | Verbose failure leakage | ✅ | Resilience4j returns terse JSON fallbacks, not stack traces. |
| E | Bypassing the gateway to reach the core directly | ⚠️ | Depends on network segmentation in deployment; the core must not be publicly reachable (deployment control). |

**Note:** the gateway does **not** authenticate. If deployed with the core
exposed on a routable network, an attacker could skip rate limiting. The
mitigation is network policy — the core listens only on the internal network.

### 4.3 Commerce Core (trust anchor)

| STRIDE | Threat | Status | Mitigation / note |
|---|---|---|---|
| S | Impersonation via forged/expired token | ✅ | `JwtService` verifies HMAC signature with `verifyWith(key)` (pins the algorithm to the key, blocking `alg=none` downgrade) and rejects expired tokens; invalid tokens clear the security context. |
| S | Credential stuffing / brute force on login | ⚠️ | BCrypt slows offline cracking; gateway rate-limits per IP. Residual: no per-account lockout or progressive delays yet. |
| T | Mass-assignment / overposting | ✅ | Requests bind to **DTOs**, not entities; entities are never deserialized from clients. |
| T | SQL injection | ✅ | Spring Data JPA / parameterized queries; no string-concatenated SQL. |
| R | Denying an action (e.g. coupon use, status change) | ✅ | `audit` domain records ORDER_*, PAYMENT_*, COUPON_USED with actor + timestamp. |
| I | Leaking entities / internals in responses or errors | ✅ | DTO-only responses; `GlobalExceptionHandler` returns structured errors with no stack traces. |
| I | IDOR — reading/modifying another user's cart/order | ⚠️ | Ownership is enforced in services; **verify on every new endpoint** (see checklist). This is the highest-value class to test deliberately. |
| D | Expensive endpoints / unbounded queries | ⚠️ | Gateway rate limiting + pagination on list endpoints; per-endpoint cost limits are a tracked improvement. |
| E | Privilege escalation to admin | ✅ | `/api/admin/**` requires `ROLE_ADMIN`; authorities are DB-sourced; role claim is non-authoritative. |
| E | Business-logic abuse (negative quantities, price=0, coupon stacking) | ✅ | Bean Validation + server-side revalidation of stock, active products, and coupons during checkout. |

### 4.4 PostgreSQL (data tier)

| STRIDE | Threat | Status | Mitigation / note |
|---|---|---|---|
| T | Unauthorized schema change / drift | ✅ | Flyway migrations + `ddl-auto: validate`; app fails fast on schema mismatch. |
| I | Credential exposure | ⚠️ | Credentials via env vars; compose ships dev defaults (see `.env.example`). Production must inject secrets. |
| I | Data at rest exposure | ❌ | No encryption at rest in the local stack — a deployment/infra control. |
| E | Over-privileged DB account | ⚠️ | Use a least-privilege app role (no superuser/DDL in prod) — deployment hardening. |

### 4.5 Kafka & the event tier

| STRIDE | Threat | Status | Mitigation / note |
|---|---|---|---|
| S | Forged/unauthorized producer or consumer | ❌→infra | No broker auth in local plaintext mode; production needs SASL/mTLS + ACLs. |
| T | Phantom or lost events (dual-write problem) | ✅ | **Transactional outbox**: an event exists iff its business transaction committed; relay uses `FOR UPDATE SKIP LOCKED`. |
| R | Replay / duplicate processing | ✅ | At-least-once delivery + **idempotent consumer** (`ProcessedEventTracker` dedupes on `eventId`, marked only after success). |
| D | Poison message stalls a consumer | ✅ | `DefaultErrorHandler` with backoff + per-topic **Dead Letter Topics**; `NonRetryableEventException` routes poison messages straight to DLT. |
| I | Sensitive data on the wire | ⚠️ | Events carry business identifiers; minimize PII in payloads; encrypt transport in prod. |

### 4.6 Notification-service & SMTP

| STRIDE | Threat | Status | Mitigation / note |
|---|---|---|---|
| T | Acting on a malformed/hostile event | ✅ | `@JsonIgnoreProperties(ignoreUnknown = true)` + explicit poison handling; consumers own their event classes (contract = topic + JSON shape). |
| D | Retry storms on transient SMTP failure | ✅ | Bounded retries with backoff, then DLT; dedup prevents duplicate sends on redelivery. |
| I | Email content / header injection | ⚠️ | Template-driven mail; ensure event-derived fields are encoded, not concatenated into headers. |

### 4.7 Redis (rate-limit state)

| STRIDE | Threat | Status | Mitigation / note |
|---|---|---|---|
| S/I | Unauthenticated access | ❌→infra | No auth in local mode; production needs `requirepass`/ACL + network isolation. |
| D | Redis outage disables rate limiting | ⚠️ | Rate limiting degrades if Redis is down; treat Redis as part of the security-critical path. |

## 5. Cross-cutting residual risks (prioritized backlog)

| Pri | Risk | Action |
|---|---|---|
| P1 | **IDOR on object-scoped endpoints** | Add ownership-assertion tests for every customer resource; cover in `vulnerable-lab`. |
| P1 | **No token revocation** | Document logout semantics; consider short TTL + refresh token with server-side revocation list. |
| P2 | **No account lockout / throttling on auth** | Add per-account attempt throttling distinct from IP rate limiting. |
| P2 | **Missing HTTP security headers** | Add HSTS, CSP, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` at the gateway. |
| P2 | **CORS too broad for prod** | Per-environment allow-list; drop `localhost:*` outside dev. |
| P3 | **Plaintext transport & no at-rest encryption (local)** | TLS for DB/Redis/Kafka/SMTP; broker SASL/mTLS + ACLs in deployed environments. |
| P3 | **Containers run as root** | Non-root user + minimal base images (DevSecOps phase). |

These are intentionally visible. An AppSec program is judged by how it tracks
and burns down residual risk, not by claiming zero.

## 6. Assumptions & out of scope

- **Assumed:** TLS terminates at the edge in any non-local deployment; the core,
  data and event tiers are not reachable from the public internet; secrets are
  injected from a real secret manager in production.
- **Out of scope here:** the simulated payment processor (no real PCI scope),
  physical/host security, and the CI/CD supply chain controls — those are covered
  in [`../devops/cicd-security.md`](../devops/cicd-security.md).

## 7. Maintenance

Update this model when a trust boundary moves: a new external integration, a new
public endpoint, a new event topic/consumer, or a change to how authentication
or authorization is enforced. Each pull request that touches `config/`,
`security/`, `messaging/`, or the gateway routing should re-check the relevant
section above.
