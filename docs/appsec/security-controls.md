# Security Controls Catalog

A control-centric inventory: **what** protects Aurora, **where** it lives in the
codebase, and **how** to verify it. Use this when hardening a specific area or
when reviewing a change that touches security-sensitive code.

This complements the risk view in [`owasp-top-10.md`](owasp-top-10.md) and the
design view in [`threat-model.md`](threat-model.md).

Paths are relative to the repository root.

---

## 1. Authentication

| Control | Where | Notes |
|---|---|---|
| Stateless JWT Bearer auth | `backend/.../config/SecurityConfig.java` | `SessionCreationPolicy.STATELESS`; CSRF/formLogin/httpBasic/logout disabled by design. |
| JWT issuance | `backend/.../security/jwt/JwtService.java` (`generateToken`) | Subject = email; `userId` + `role` claims; `issuedAt`/`expiration` set from `JwtProperties`. |
| JWT verification | `JwtService` (`extractClaims`, `isTokenValid`) | `Jwts.parser().verifyWith(key)` pins the algorithm to the key → blocks `alg=none`/confusion. Checks subject match + expiry. |
| Bearer extraction & context binding | `backend/.../security/jwt/JwtAuthenticationFilter.java` | `OncePerRequestFilter`; on any `JwtException`/`IllegalArgumentException` it clears the security context and continues unauthenticated. |
| Credential check | `SecurityConfig.authenticationProvider` | `DaoAuthenticationProvider` + `UserDetailsService`. |
| Password hashing | `SecurityConfig.passwordEncoder` | `BCryptPasswordEncoder` (per-password salt, adaptive cost). |
| Disabled-account enforcement | `SecurityConfig.userDetailsService` | `.disabled(!user.isEnabled())` — disabled users cannot authenticate. |
| Secret configuration | `JwtProperties` / `APP_SECURITY_JWT_SECRET` | Externalized; ≥32 chars required in prod (`CLAUDE.md`). |

**Verify:** a request with no/invalid/expired token to a protected route returns
`401`; a tampered signature is rejected; a disabled user cannot log in.

---

## 2. Authorization

| Control | Where | Notes |
|---|---|---|
| URL authorization rules | `SecurityConfig.securityFilterChain` (`authorizeHttpRequests`) | Public: health/info/prometheus, auth POSTs, public catalog GETs. `/api/admin/**` → `ROLE_ADMIN`. Default `anyRequest().authenticated()`. |
| Authorities from DB (not token) | `SecurityConfig.userDetailsService` | Maps `ROLE_<role>` from the persisted user — the JWT `role` claim is **non-authoritative**, defeating claim tampering. |
| Object-level ownership | domain services (`cart`, `order`, `review`, `wishlist`) | Enforced per-operation; **must be asserted on every new resource endpoint** (IDOR is the key residual risk — see threat model A01). |
| Custom 401/403 responses | `SecurityConfig.writeErrorResponse` | Structured `ErrorResponse` JSON via `authenticationEntryPoint` / `accessDeniedHandler`. |

**Verify:** a `ROLE_CUSTOMER` token gets `403` on `/api/admin/**`; a customer
cannot read another customer's order by id.

---

## 3. Input validation & output safety

| Control | Where | Notes |
|---|---|---|
| Bean Validation on inputs | request DTOs, e.g. `backend/.../auth/dto/RegisterRequest.java` | `@Email`, `@Size`, `@NotBlank`; password 8–72 chars (bcrypt cap). |
| DTO binding (no overposting) | controllers/services across domains | Entities are never bound from request bodies. |
| Entities never serialized out | all controllers | Responses are DTOs only — limits data exposure and coupling. |
| Parameterized data access | Spring Data JPA repositories | No string-concatenated SQL. |
| Centralized error mapping | `backend/.../common/exception/GlobalExceptionHandler.java` | Maps `NotFound`/`Business`/validation exceptions to `common.api` `ErrorResponse`; **no stack traces** leak. |

**Verify:** malformed/oversized input yields `400` with field errors; server
errors never expose stack traces or SQL.

---

## 4. Business-logic integrity (never trust the client)

| Control | Where | Notes |
|---|---|---|
| Server-side price/total computation | `backend/.../checkout/service/CheckoutService.java` | Line prices and totals computed from products/variants, not request values. |
| Stock revalidation + deduction | `CheckoutService` | Active-product and available-stock checks; `SALE` stock movement recorded. |
| Coupon revalidation | `checkout` + `promotion` | Coupon re-checked at checkout; usage recorded. |
| Simulated payment integrity | `backend/.../payment/service/PaymentService.java` | Payment state transitions recorded; events emitted via outbox. |

**Verify:** submitting a manipulated price/quantity/coupon in the request has no
effect on the persisted order total.

---

## 5. Edge controls (gateway)

| Control | Where | Notes |
|---|---|---|
| Single entry point + routing | `gateway/.../resources/application.yml` | `/api/notifications/**` before the `/api/**` core catch-all (order-sensitive). |
| Per-client-IP rate limiting | `application.yml` (`RequestRateLimiter`) + `gateway/.../config` `clientIpKeyResolver` | Redis token bucket; `replenishRate`/`burstCapacity` env-tunable. |
| CORS allow-list | `application.yml` `globalcors` | Methods + origins constrained; `DedupeResponseHeader` avoids duplicate CORS headers. ⚠️ tighten origins per environment. |
| Circuit breakers | `application.yml` `resilience4j.circuitbreaker` | Sliding window 20, 50% failure threshold, 10s open. |
| GET-only retries | route `Retry` filters | Only idempotent reads retried (2×, `SERVER_ERROR`). |
| Per-call timeouts | `resilience4j.timelimiter` | Default 5s; cancels running futures. |
| Graceful fallbacks | `gateway/.../fallback/FallbackController.java` | Terse JSON `503` instead of hanging or leaking internals. |

**Verify:** exceeding the rate limit returns `429`; a downed core returns the
JSON fallback, not a stack trace or a hang.

---

## 6. Messaging integrity & resilience

| Control | Where | Notes |
|---|---|---|
| Transactional outbox | `backend/.../messaging/outbox/` (`OutboxEventRecorder`, `OutboxRelay`) | Event row written in the same DB tx as the business change; relay drains PENDING → Kafka. No dual-write. |
| Concurrent-safe relay | `OutboxRelay` claim query | `FOR UPDATE SKIP LOCKED` → multiple core instances relay without duplicates. |
| Idempotent consumption | `services/notification-service/.../listener/ProcessedEventTracker.java` | Dedupe on `eventId`; marked processed only after successful delivery. |
| Poison-message handling | `.../listener/NonRetryableEventException.java` + `DefaultErrorHandler` | Backoff + retry for transient errors; immediate DLT for poison; per-topic `<topic>.DLT`. |
| Forward-compatible contracts | event records (`@JsonIgnoreProperties(ignoreUnknown = true)`) | No shared library; contract = topic + JSON shape; no native deserialization. |
| Topic registry | `backend/.../messaging/AuroraTopics.java` | Centralized topic names; publishing toggle `app.events.enabled`. |

**Verify:** a rolled-back transaction emits no event; a redelivered event sends
only one email; a malformed event lands in the DLT without stalling the consumer.

---

## 7. Data & schema

| Control | Where | Notes |
|---|---|---|
| Versioned migrations | `backend/src/main/resources/db/migration/V*__*.sql` | Flyway; e.g. `V6__create_event_outbox.sql`. |
| Schema validation at boot | `application.yml` (`ddl-auto: validate`) | App fails fast on schema drift; Hibernate never alters tables. |
| Audit trail | `backend/.../audit/` | Sensitive business events persisted; admin-readable. |

---

## 8. Observability (detective controls)

| Control | Where | Notes |
|---|---|---|
| Metrics | all services `/actuator/prometheus` | Behind Spring Security in the core; permitted explicitly. |
| Distributed tracing | Micrometer Tracing/Brave | `traceId`/`spanId` in log pattern across gateway → core → consumer. |
| Health probes | `/actuator/health` | Used by compose healthchecks for ordered startup. |

---

## 9. Configuration & secrets

| Control | Where | Notes |
|---|---|---|
| Externalized secrets | env vars (`APP_SECURITY_JWT_SECRET`, datasource creds) | No secrets hard-coded in source. |
| Local dev defaults | `docker-compose.yml`, `.env.example` | Dev-only defaults; must be overridden in deployed environments. |
| Production overrides | `CLAUDE.md` "Production config to override" | JWT secret + expiration documented. |

---

## Control-to-document cross-reference

- **Why** each control exists and what it defends against → [`threat-model.md`](threat-model.md).
- **How** controls map to industry risk categories → [`owasp-top-10.md`](owasp-top-10.md).
- **How** controls are verified automatically → [`security-testing.md`](security-testing.md) and [`../devops/cicd-security.md`](../devops/cicd-security.md).
