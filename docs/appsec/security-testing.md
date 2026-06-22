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

The security-critical core is tested directly — mostly with no Spring context at
all (pure Mockito), plus one fast web slice for the authorization wiring. None
need a database, so they are fast and reliable in CI.

| Test | File | Asserts |
|---|---|---|
| `JwtServiceTest` | `backend/src/test/java/com/aurora/backend/security/jwt/JwtServiceTest.java` | A valid token round-trips; a **tampered payload** is rejected; a token **signed with another secret** is rejected; an **expired token** is rejected. (OWASP A02/A07) |
| `JwtAuthenticationFilterTest` | `backend/.../security/jwt/JwtAuthenticationFilterTest.java` | No header → stays anonymous; valid token → authenticated with **authorities loaded from the database, not the token**; invalid token → fails closed but the chain proceeds. (OWASP A01) |
| `OrderServiceTest` | `backend/.../order/service/OrderServiceTest.java` | `getUserOrder` resolves only via the **owner-scoped** `findByIdAndUserId` (never the unscoped `findById`), so a customer cannot read another customer's order. Locks the **IDOR** control. (OWASP A01) |
| `CheckoutServiceTest` | `backend/.../checkout/service/CheckoutServiceTest.java` | Order + payment money is **recomputed server-side** from the cart/catalog (client cannot influence price/total); empty cart, inactive variant and insufficient stock are rejected. Locks the **client-trusted-pricing** control. (OWASP A04) |
| `CouponServiceTest` | `backend/.../promotion/service/CouponServiceTest.java` | Percentage/fixed discount math, discount **capped at subtotal** (no negative orders), inactive/expired coupons contribute nothing, and **global + per-user use limits** are enforced (no reuse beyond limit). (OWASP A04) |
| `AdminAuthorizationTest` | `backend/.../config/AdminAuthorizationTest.java` | **Web-slice** test of the real `SecurityConfig` filter chain: `/api/admin/**` returns **401 anonymous, 403 for `ROLE_CUSTOMER`, 200 for `ROLE_ADMIN`**. Covers the RBAC *wiring*, not just the logic. (OWASP A01) |
| `ReviewServiceTest` | `backend/.../review/service/ReviewServiceTest.java` | One review per user per product (no duplicates); reviews can only be created/listed for an **active** product (a hidden product leaks nothing); a new review is never auto-`verifiedPurchase`. (OWASP A04) |
| `CartServiceTest` | `backend/.../cart/service/CartServiceTest.java` | Add-to-cart prices from the **catalog** (not the client) and rejects inactive variants / insufficient stock; cart items are **owner-scoped** (`findByIdAndCartUserId`), so a customer cannot update/remove another's item by id. (OWASP A04 + A01/IDOR) |
| `InventoryServiceTest` | `backend/.../inventory/service/InventoryServiceTest.java` | Stock invariants: quantities must be positive, available stock can never go negative (no overselling), and reserve/release keep the available/reserved split consistent. (integrity / A04) |
| `AuthValidationTest` | `backend/.../auth/controller/AuthValidationTest.java` | **Web-slice**: blank/invalid/malformed register bodies return a clean **400 `VALIDATION_ERROR`** (malformed JSON → `400 BAD_REQUEST`) via `GlobalExceptionHandler`, never a 500, and the service is not invoked with bad input. (OWASP A03) |
| `WishlistServiceTest` | `backend/.../wishlist/service/WishlistServiceTest.java` | Wishlist entries require an active product, are de-duplicated per user, and are removed via a **user-scoped** lookup (no cross-user deletes). (OWASP A04 + A01) |
| `ProductServiceTest` | `backend/.../catalog/product/service/ProductServiceTest.java` | Search input validation; an empty product list **skips the rating query** (guards the no-N+1 aggregation); unknown slug → 404. |

The headline assertion — *authorities come from the DB, not the JWT claim* — is
the control that makes a forged `role` claim worthless. It is now covered by a
regression test, so a future refactor can't silently break it.

Run them:

```powershell
cd backend
.\mvnw.cmd test "-Dtest=JwtServiceTest,JwtAuthenticationFilterTest"
```

> Current status: **51 tests, all passing.** (JWT ×7, `OrderServiceTest` ×2,
> `CheckoutServiceTest` ×4, `CouponServiceTest` ×9, `AdminAuthorizationTest` ×3,
> `ReviewServiceTest` ×4, `CartServiceTest` ×5, `InventoryServiceTest` ×6,
> `AuthValidationTest` ×4, `WishlistServiceTest` ×3, `ProductServiceTest` ×4.)
> A parallel **frontend** suite (Vitest) covers the auth service, route guards,
> the HTTP interceptor, cart/toast services and utilities (39 tests).
> The JWT and service tests need no Spring context; `AdminAuthorizationTest` is a
> web slice (Spring context, still no database/Docker).

### Where to add the next ones

The highest-value controls are now locked: **IDOR** on orders and
**client-trusted pricing** at checkout at the service layer, **coupon abuse** in
`CouponService`, and the **`/api/admin/**` RBAC wiring** end-to-end through the
real filter chain (`AdminAuthorizationTest`). Remaining gaps:

- **Full-stack authz (integration):** with a test database (Testcontainers
  PostgreSQL), drive the same checks through a real JWT issued by `/api/auth/login`
  and the running security chain end-to-end. (Needs Docker — runs in CI, not in a
  Docker-less dev shell.)
- **IDOR breadth:** orders and cart items are now covered (`OrderServiceTest`,
  `CartServiceTest`); the remaining surfaces are admin-only resources, already
  gated by the `/api/admin/**` rule (`AdminAuthorizationTest`).
- **Validation:** the auth endpoints are covered (`AuthValidationTest`); the same
  `@WebMvcTest` pattern can be extended to the other validated controllers
  (checkout/cart/review request bodies).

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
