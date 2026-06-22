# Security Testing Strategy

How Aurora Marketplace is tested for security — the automated layers, the
security-specific unit tests that ship in the repo, and a **manual pentest
checklist** for changes that automation can't fully judge. This operationalizes
the risks in [`threat-model.md`](threat-model.md) and [`owasp-top-10.md`](owasp-top-10.md).

## The testing pyramid (security view)

```
        ▲  fewer, slower, higher-fidelity
        │   ┌───────────────────────────────┐
        │   │  Manual pentest / exploratory │   ← checklist below; vulnerable-lab
        │   ├───────────────────────────────┤
        │   │  DAST (running app)           │   ← NightVision (CI, opt-in) + ZAP (local)
        │   ├───────────────────────────────┤
        │   │  Integration / API tests      │   ← planned (see §2)
        │   ├───────────────────────────────┤
        │   │  Security unit tests          │   ← shipped: JWT, order IDOR, checkout pricing
        │   ├───────────────────────────────┤
        │   │  SAST · SCA · secrets (CI)    │   ← every push/PR, see cicd-security
        ▼   └───────────────────────────────┘
            more, faster, run on every commit
```

> **What runs today vs. planned.** The bottom two layers are live: the CI static
> scanners (§1) on every push/PR, and the JWT security unit tests (§2). **DAST** is
> wired as an opt-in workflow (§3, NightVision in CI + ZAP locally). **Integration
> / API authz tests** are the single highest-value gap to close (§2).

## 1. Automated in CI (every push/PR)

These run in [`security.yml`](../../.github/workflows/security.yml) — see
[`../devops/cicd-security.md`](../devops/cicd-security.md) for details.

| Layer | Tool | Catches |
|---|---|---|
| SAST | CodeQL | Code-level vulns (injection, unsafe patterns) |
| SCA | Trivy fs | Vulnerable dependencies |
| Secrets | Gitleaks | Committed credentials (**hard gate**) |
| IaC/image | Trivy config + Hadolint | Container/compose misconfig |
| SBOM | CycloneDX | Supply-chain inventory |

## 2. Security unit tests (shipped in the repo)

The authentication core is tested directly, with no database or Spring context,
so the tests are fast and reliable in CI.

| Test | File | Asserts |
|---|---|---|
| `JwtServiceTest` | `backend/src/test/java/com/aurora/backend/security/jwt/JwtServiceTest.java` | A valid token round-trips; a **tampered payload** is rejected; a token **signed with another secret** is rejected; an **expired token** is rejected. (OWASP A02/A07) |
| `JwtAuthenticationFilterTest` | `backend/.../security/jwt/JwtAuthenticationFilterTest.java` | No header → stays anonymous; valid token → authenticated with **authorities loaded from the database, not the token**; invalid token → fails closed but the chain proceeds. (OWASP A01) |
| `OrderServiceTest` | `backend/.../order/service/OrderServiceTest.java` | `getUserOrder` resolves only via the **owner-scoped** `findByIdAndUserId` (never the unscoped `findById`), so a customer cannot read another customer's order. Locks the **IDOR** control. (OWASP A01) |
| `CheckoutServiceTest` | `backend/.../checkout/service/CheckoutServiceTest.java` | Order + payment money is **recomputed server-side** from the cart/catalog (client cannot influence price/total); empty cart, inactive variant and insufficient stock are rejected. Locks the **client-trusted-pricing** control. (OWASP A04) |

The headline assertion — *authorities come from the DB, not the JWT claim* — is
the control that makes a forged `role` claim worthless. It is now covered by a
regression test, so a future refactor can't silently break it.

Run them:

```powershell
cd backend
.\mvnw.cmd test "-Dtest=JwtServiceTest,JwtAuthenticationFilterTest"
```

> Current status: **13 tests, all passing.** (JWT ×7, `OrderServiceTest` ×2,
> `CheckoutServiceTest` ×4.)

### Where to add the next ones

The two highest-value controls — **IDOR** on orders and **client-trusted pricing**
at checkout — are now locked at the service layer (`OrderServiceTest`,
`CheckoutServiceTest`). The remaining gaps are the *end-to-end* and *breadth*
variants:

- **Authorization (integration):** with a test database (Testcontainers
  PostgreSQL), assert `/api/admin/**` returns `403` for a `ROLE_CUSTOMER` token
  and `200` for `ROLE_ADMIN`, exercising the real security filter chain
  (`spring-security-test`). The unit tests cover the *logic*; this covers the
  *wiring*. (Needs Docker — runs in CI, not in a Docker-less dev shell.)
- **IDOR breadth:** extend the ownership assertion to cart and review resources
  by id, mirroring `OrderServiceTest`.
- **Validation:** assert oversized/blank/negative inputs yield `400`, not `500`.

## 3. DAST — dynamic testing (the running app)

Static analysis reads code; DAST attacks the **live** API. Aurora has two ways to
run it.

### a) NightVision — automated, in CI ([`dast.yml`](../../.github/workflows/dast.yml))

A *white-box-assisted* DAST: it extracts an OpenAPI spec from the Spring Boot
source (**API Discovery**), boots the stack, scans the gateway with the **ZAP +
Nuclei** engines, and traces each finding back to the exact source line (**Code
Traceback**), uploading SARIF to the Security tab. It runs on a weekly schedule
and on manual dispatch, and is **opt-in** — it no-ops unless the
`NIGHTVISION_TOKEN` secret is set. Setup and details are in
[`../devops/cicd-security.md`](../devops/cicd-security.md) §6.

### b) OWASP ZAP — free baseline, locally (no account needed)

```powershell
docker compose --profile apps up -d --build
# Baseline passive scan through the gateway
docker run --rm -t ghcr.io/zaproxy/zaproxy:stable zap-baseline.py `
  -t http://host.docker.internal:8088 -r zap-report.html
```

Either way, focus DAST on: missing security headers, CORS behavior, error
handling/leakage, and authentication edge cases at the edge — exactly the
runtime-only issues the static scanners can't see.

## 4. Manual penetration-test checklist

Run through this before merging anything that touches auth, a new endpoint, or
the gateway. It is organized by OWASP category. ☐ = verify per change.

### Access control (A01)
- ☐ Every customer-scoped endpoint asserts **ownership** (try another user's
  resource id → must be `403`/`404`, never the data).
- ☐ `/api/admin/**` rejects a `ROLE_CUSTOMER` token (`403`).
- ☐ Removing/altering the token gives `401`, not partial access.
- ☐ A token with a hand-edited `role` claim does **not** elevate privilege.

### Authentication & tokens (A02/A07)
- ☐ Login with wrong password fails identically to unknown user (no enumeration).
- ☐ Expired token is rejected.
- ☐ Token signed with a different secret is rejected.
- ☐ Disabled account cannot authenticate.
- ☐ Repeated failed logins are throttled (IP rate limit today; per-account TODO).

### Injection & input (A03)
- ☐ SQL metacharacters in inputs do not alter queries (parameterized).
- ☐ Oversized / malformed / negative values → `400` with field errors.
- ☐ Event-derived strings cannot inject email headers (CRLF) in notifications.

### Business logic (A04)
- ☐ Manipulated price/total/quantity in the request is ignored (server recomputes).
- ☐ Out-of-stock / inactive product cannot be purchased.
- ☐ Coupons cannot be stacked or reused beyond their limit.

### Misconfiguration (A05)
- ☐ Errors return clean JSON — no stack traces, SQL, or internal paths.
- ☐ Actuator: only health/info/prometheus are public; the rest require auth.
- ☐ CORS rejects unexpected origins (per environment).
- ☐ Rate limit returns `429` when exceeded.

### Messaging integrity (A08)
- ☐ A rolled-back transaction emits **no** event (outbox).
- ☐ A redelivered event is processed **once** (idempotency).
- ☐ A poison event lands in the `.DLT`, not an infinite retry loop.

### Logging & monitoring (A09)
- ☐ Sensitive actions appear in the audit log with actor + timestamp.
- ☐ A request is traceable end-to-end via `traceId`.

## 5. Triage & severity

Use CVSS-style judgement: prioritize by *impact × likelihood*. A confirmed IDOR
on order data outranks a missing header. Track findings as issues; link them
back to the threat model item they realize, and add a regression test when fixed.
