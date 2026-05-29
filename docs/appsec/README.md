# Application Security (AppSec) Program

This directory documents the **security posture** of Aurora Marketplace and the
**DevSecOps practices** wrapped around it. It is written to be useful in two
ways: as a working reference for hardening the platform, and as evidence of how
security is reasoned about, implemented, and automated end to end.

Aurora is an intentionally **security-focused portfolio project**. The guiding
rule — *never trust client-supplied values for prices, totals, stock, roles,
ownership, or authorization* — is enforced in code, and this program documents
where and how.

## Document map

| Document | What it covers |
|---|---|
| [`threat-model.md`](threat-model.md) | STRIDE threat model: assets, trust boundaries, attack surface, and mitigations mapped to code. **Start here.** |
| [`owasp-top-10.md`](owasp-top-10.md) | OWASP Top 10 (2021) mapped to concrete controls, file references, and remediation status. |
| [`security-controls.md`](security-controls.md) | Catalog of implemented security controls and where each lives in the codebase. |
| [`security-testing.md`](security-testing.md) | Security test strategy: SAST, SCA, secret scanning, DAST, and a manual pentest checklist. |
| [`vulnerable-lab.md`](vulnerable-lab.md) | Design of the deliberately-vulnerable `vulnerable-lab` branch and exploitation → remediation writeups. |
| [`../devops/cicd-security.md`](../devops/cicd-security.md) | The DevSecOps pipeline: security gates in CI/CD and how to read the reports. |
| [`../../SECURITY.md`](../../SECURITY.md) | Vulnerability disclosure policy (repository root). |

Related architecture docs:
[`02_backend_architecture.md`](../architecture/02_backend_architecture.md) ·
[`03_event_driven_microservices.md`](../architecture/03_event_driven_microservices.md).

## System under protection

```
Browser ──TLS*──> Angular SPA (4200)
                      │  Bearer JWT
                      ▼
              ┌───────────────┐   per-IP rate limit (Redis token bucket)
              │  Gateway 8088 │   CORS · circuit breaker · retries (GET) · timeouts
              └───────┬───────┘
                      │ /api/**
          ┌───────────┴───────────┐
          ▼                       ▼
  ┌───────────────┐       ┌────────────────────┐
  │  Core 8080    │       │ notification 8082  │
  │  Spring Sec   │       │ (event consumer)   │
  │  JWT · RBAC   │       └─────────▲──────────┘
  └──┬────────┬───┘                 │ at-least-once
     │        │ outbox relay        │ (idempotent)
     ▼        ▼                     │
 PostgreSQL  Kafka ─────────────────┘
 (5433)      (events)        SMTP → Mailpit
```

\* TLS is a deployment concern; the local compose stack runs plaintext (see gaps).

## Security posture at a glance

The platform was built with security controls in place from the start. The table
below is the honest current state — green where a control exists in code, amber
where it is partial, red where it is a known gap. Gaps are tracked, not hidden;
that is the point of an AppSec program.

### Implemented ✅

- **Stateless JWT authentication** — HMAC-signed Bearer tokens, signature verified
  on every request (`security/jwt/JwtService.java`, `JwtAuthenticationFilter.java`).
- **Role-based authorization** — `ROLE_CUSTOMER` / `ROLE_ADMIN`; `/api/admin/**`
  is admin-only; authorities are **reloaded from the database**, not trusted from
  the token claim (`config/SecurityConfig.java`).
- **Password hashing** — BCrypt with per-password salt (`SecurityConfig.passwordEncoder`).
- **Server-side recomputation** — prices, totals, stock and discounts are
  recalculated in `checkout`/`payment` services; client values are never trusted.
- **Input validation** — Jakarta Bean Validation on all request DTOs.
- **Uniform error handling** — `common.exception.GlobalExceptionHandler` returns
  structured JSON with no stack traces or internal details.
- **Per-client-IP rate limiting** — Redis token bucket at the gateway.
- **Resilience** — circuit breakers, GET-only retries, per-call timeouts, JSON
  fallbacks (Resilience4j).
- **Reliable, idempotent messaging** — transactional outbox (no dual-write) +
  consumer-side dedup + Dead Letter Topics.
- **Audit logging** — sensitive business events recorded (`audit` domain).
- **Observability** — Prometheus metrics and `traceId`/`spanId` correlation in
  logs across all three services.
- **Schema discipline** — Flyway migrations with `ddl-auto: validate`.

### Partial ⚠️

- **CORS** — locked to `localhost` origins for dev; must be tightened per
  environment before production.
- **Secrets management** — externalized via environment variables, but the local
  compose stack ships development defaults inline (see `.env.example`).
- **Token lifecycle** — short-lived access tokens only; no refresh/rotation and
  no server-side revocation (a stolen token is valid until it expires).

### Gaps ❌ (tracked)

- **HTTP security headers** — no HSTS / CSP / `X-Content-Type-Options` /
  `X-Frame-Options` set by the app (expected at the gateway/edge).
- **Transport encryption in the stack** — Postgres, Redis, Kafka and SMTP run
  plaintext in compose; TLS is delegated to the deployment environment.
- **Container hardening** — base images and non-root execution (addressed in the
  DevSecOps phase; see `../devops/cicd-security.md`).
- **Account-recovery & verification flows** — email verification and password
  reset are not yet implemented.

Each item above is expanded — with concrete remediation — in
[`owasp-top-10.md`](owasp-top-10.md) and [`threat-model.md`](threat-model.md).

## Defense in depth

Aurora layers controls so that no single failure is catastrophic:

1. **Edge (gateway)** — single entry point, CORS, per-IP rate limiting, circuit
   breakers and timeouts absorb abuse and protect the core from overload.
2. **Application (core)** — authentication, RBAC, input validation, and
   server-side recomputation of every security-sensitive value.
3. **Data** — parameterized access via JPA, Flyway-validated schema, least-data
   exposure through DTOs (entities are never serialized to clients).
4. **Messaging** — the outbox guarantees events match committed transactions;
   consumers are idempotent and poison messages are quarantined in DLTs.
5. **Operations** — audit logs, metrics and distributed tracing make abuse and
   failures observable.

## How to use this program

- Reviewing the design? Read the **threat model**, then the **OWASP mapping**.
- Hardening a specific area? Find the control in **security-controls.md** and
  follow the file references.
- Adding a feature? Run it past the threat model's trust boundaries and the
  manual checklist in **security-testing.md** before merging.
- Verifying automation? See **cicd-security.md** for the pipeline gates.
