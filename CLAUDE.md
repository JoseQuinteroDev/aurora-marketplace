# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Aurora Marketplace is a full-stack, **event-driven** e-commerce platform built as a portfolio/AppSec project. A cohesive commerce core (Spring Boot) owns the transactional domain and publishes domain events to Kafka; an API gateway is the single entry point; decoupled microservices (currently `notification-service`) react to events. The Angular storefront/admin enters through the gateway.

```
Angular (4200) --/api/**--> Gateway (8088) --> Core (8080) --events--> Kafka --> notification-service (8082) --SMTP--> Mailpit
                                                  Core --- PostgreSQL
```

Read these before non-trivial work:
- `docs/architecture/02_backend_architecture.md` — core domain, security model, checkout flow.
- `docs/architecture/03_event_driven_microservices.md` — topics, event contracts, resilience.
- `docs/appsec/README.md` + `docs/appsec/security-master-plan.md` — AppSec posture and the phased hardening roadmap; read before security work.
- `AGENTS.md` — original working rules and package conventions (note: some are historical milestones; see below).

## Repository layout

| Path | What |
|---|---|
| `backend/` | Core commerce service (`com.aurora.backend`), Java 21 / Spring Boot 3.5 / Maven |
| `gateway/` | Spring Cloud Gateway — single entry point, routing, CORS, Resilience4j fallback |
| `services/notification-service/` | Event consumer → transactional email (`com.aurora.notification`) |
| `frontend/` | Angular 21 storefront + admin (`src/app`) |
| `docker-compose.yml` | Infra (default) and full app stack (`--profile apps`) |
| `docs/` | Architecture, API, AppSec, DevOps, and design docs |

Each of the three Java modules is an **independent Maven build** with its own `mvnw`/`mvnw.cmd`, `pom.xml`, and `Dockerfile`. There is no parent POM and **no shared library** — producer and consumers each own their event classes by design, so the contract is the Kafka topic name + JSON shape (see `backend/.../messaging/event/*` vs `notification-service/.../event/*`).

## Commands

Shell is PowerShell on Windows; use `.\mvnw.cmd` (Bash `./mvnw` also available).

### Infrastructure
```powershell
docker compose up -d                         # Postgres, Kafka, Redis, MinIO, Mailpit — run apps locally
docker compose --profile apps up -d --build  # full stack incl. gateway + core + notification-service
```

### Backend / gateway / notification-service (same pattern, per module dir)
```powershell
cd backend                       # or gateway, or services\notification-service
.\mvnw.cmd spring-boot:run       # run
.\mvnw.cmd clean install         # build + test
.\mvnw.cmd clean install -DskipTests
.\mvnw.cmd test                  # all tests
.\mvnw.cmd test -Dtest=BackendApplicationTests        # single test class
.\mvnw.cmd test -Dtest=SomeClass#someMethod           # single test method
```

### Frontend (in `frontend/`)
```powershell
npm install
npm start                        # ng serve via proxy.conf.js -> core on :8080
$env:AURORA_API_TARGET="http://localhost:8088"; npm start   # route through the gateway instead
npm run build
npm run watch                    # incremental dev build (rebuild on change)
npm test                         # Vitest (jsdom) via the Angular builder — runs once, CI-friendly
npm run test:watch               # watch mode
```
There are **no** `lint`/`format` npm scripts — Prettier is configured (`.prettierrc`) but run via IDE/manually.

### Testing
- **The backend suite is split by Docker need.** `@SpringBootTest` integration tests (e.g. `BackendApplicationTests`) load the full context against a **Testcontainers PostgreSQL** (`TestcontainersConfiguration`) and therefore **require a running Docker daemon**. The bulk of the suite is **pure Mockito unit tests** plus two `@WebMvcTest` slices (security/validation wiring) — **no Docker, no DB** — so they run anywhere. Prefer that style for new service/controller tests; a Docker-free single class runs with `.\mvnw.cmd test -Dtest=CheckoutServiceTest`.
- **Frontend** tests run on **Vitest** (jsdom) through the Angular builder — no browser. The CI `frontend` job gates on `npm test` (a failing unit test fails the build).
- The security- and commerce-critical controls (server-side pricing, IDOR/ownership, RBAC, coupon/stock limits, idempotency, i18n EN/ES parity) are locked by tests catalogued in **`docs/appsec/security-testing.md`** — add a regression test there when fixing a security-relevant bug.

### CI/CD & security gates (`.github/workflows/`)
- `ci.yml` — builds + tests all three Java modules (matrix) and the frontend (`npm run build` + `npm test`) on every push/PR.
- `security.yml` — DevSecOps gates on push/PR + weekly: CodeQL (SAST), Trivy (SCA + IaC/Dockerfile), Hadolint, CycloneDX SBOM, dependency review (fails PRs adding HIGH/CRITICAL CVEs), and a **Gitleaks secret scan (hard gate)**. It intentionally flags the `vulnerable-lab` branch.
- `dast.yml` — opt-in NightVision DAST against a running API (weekly + manual). Deep dive: `docs/devops/cicd-security.md`.

## Key conventions and gotchas

- **Layered, modular packages** under `com.aurora.backend`: each domain (`auth`, `catalog`, `inventory`, `cart`, `checkout`, `order`, `payment`, `promotion` (coupons), `review`, `wishlist`, `audit`, `batch`, `admin`) is a slice with its own `controller` / `service` / `dto` / `entity` / `repository`. Match this when adding features. Cross-cutting packages: `user` (the `User` entity/repository, shared by `auth`), `security` (JWT + token services — see Security below), `messaging` (outbox + Kafka), `common` (`ApiResponse`/`ErrorResponse`/`GlobalExceptionHandler`), `config`, and `dev` (local-only `DevTestController`).
- **Controllers are thin** HTTP adapters; business rules + `@Transactional` live in services. **Never return JPA entities** — always map to DTOs. Use Bean Validation on inputs; errors flow through `common.exception.GlobalExceptionHandler` and wrap in `common.api` (`ApiResponse`/`ErrorResponse`).
- **Never trust client values** for prices, totals, stock, roles, ownership, or authorization — the backend recalculates them. This is a security-focused project.
- **Security — access control**: stateless JWT Bearer; subject = user email. The `role` claim is written into the token but **NOT trusted for authorization** — `JwtAuthenticationFilter` reloads authorities from the DB via `userDetailsService` (`SecurityConfig`), so a forged/edited claim is worthless (this is exactly the fix the `vulnerable-lab` branch reverts). Authorities are `ROLE_CUSTOMER` / `ROLE_ADMIN`. Public: auth, `/actuator/health`, public catalog/review reads; everything under `/api/admin/**` requires `ROLE_ADMIN`. See `config/SecurityConfig.java`, `security/jwt/`, `security/token/`.
- **Security — auth hardening** (OWASP A07, `security/token/`): access tokens are short-lived (**15 min**); sessions are extended by **refresh-token rotation with reuse detection** — opaque, single-use, SHA-256-at-rest tokens (30-day) that rotate on `POST /api/auth/refresh`. Reuse outside a 10s grace window revokes the whole token family and denylists its access tokens (`TokenDenylistService`, checked per request; `POST /api/auth/revoke` is public so idle sessions can log out). Also: per-account **login lockout** (consecutive failures → 15-min lock), **password reset**, and **email verification** (a verified email gates checkout / order placement) — reset and verification are anti-enumeration (identical responses + a fixed latency floor). Roadmap: `docs/appsec/security-master-plan.md`.
- **Event publishing uses the Transactional Outbox pattern** — do NOT call Kafka directly from business code. In a `@Transactional` service, call `OutboxEventRecorder.record(...)` (`messaging/outbox/`); it writes an `event_outbox` row in the same DB transaction. The `@Scheduled OutboxRelay` drains PENDING rows to Kafka (`DomainEventPublisher` is the low-level sender) and marks them PUBLISHED, parking exhausted rows as FAILED. This guarantees at-least-once delivery with no dual-write. Topics live in `messaging/AuroraTopics`; disable with `app.events.enabled=false`. The producer serializes events to JSON strings (StringSerializer), so the relay sends raw JSON.
- **Consumers must be idempotent and resilient.** at-least-once delivery means redeliveries happen — the notification-service dedupes on `eventId` (`ProcessedEventTracker`, marked only after successful delivery) and uses a `DefaultErrorHandler` with exponential backoff + per-topic Dead Letter Topics (`<topic>.DLT`). Throw `NonRetryableEventException` for poison messages (immediate DLT); throw any other exception for transient failures (retried, then DLT). Each service owns its event classes — there is no shared library, and event records use `@JsonIgnoreProperties(ignoreUnknown = true)` so the contract can evolve.
- **Gateway resilience & edge hardening** (`gateway/application.yml` + `gateway/.../config`): per-client-IP Redis rate limiting (`RequestRateLimiter` + `clientIpKeyResolver`), with tiered buckets — replenish 1 / burst 3 on `/api/auth/forgot-password` and `/api/auth/resend-verification` (email-bombing), replenish 5 / burst 20 on the rest of `/api/auth/**` (login brute-force/spray), and a global per-IP bucket; `429`s carry `Retry-After`. **CORS is a per-env allow-list** (`GATEWAY_ALLOWED_ORIGINS`, default local ng-serve only — never a credentialed wildcard). A reactive `ResponseHardeningWebFilter` mirrors the core's security headers onto gateway-originated responses (fallbacks/429s/preflights) via `putIfAbsent`. `Retry` filter for GETs, Resilience4j circuit breaker + timeouts, JSON `FallbackController` (all methods → graceful 503). Routes are order-sensitive — the tight auth buckets and `/api/notifications/**` are declared before the `/api/**` core catch-all. Requires Redis (part of the infra compose).
- **Observability**: all three services expose `/actuator/prometheus` and emit `traceId`/`spanId` in logs (Micrometer Tracing/Brave). When adding actuator endpoints to the core, remember they sit behind Spring Security — permit them in `SecurityConfig`.
- **Schema is Flyway-managed** (`backend/src/main/resources/db/migration/V*__*.sql`) with `ddl-auto: validate` — never rely on Hibernate to create/alter tables; add a new `V#` migration instead. Latest is `V12`; the auth-hardening tables are `V7` (login lockout), `V8` (token denylist), `V10` (refresh tokens), `V11` (password-reset tokens), `V12` (email verification). Spring Batch owns its metadata tables; Aurora adds `batch_job_audit` for admin visibility.
- **Batch jobs** are tasklet-based (`importProductsJob`, `syncInventoryJob`, `cleanAbandonedCartsJob`); `spring.batch.job.enabled=false` (triggered via `/api/admin/batch/**`). CSV paths are env-overridable (`APP_BATCH_*`).
- **Gateway** routes `/api/**` → core, strips/rewrites actuator paths, and returns a JSON fallback (`/fallback/core`) via Resilience4j when the core is down.
- **Frontend** is standalone Angular 21 (signals, `ApplicationConfig`, lazy-loaded feature routes) with Tailwind, organized as `core/` (i18n, theme, models), `services/`, `guards/`, `interceptors/`, `layout/`, `shared/` (reusable components — there is **no** `design-system/` folder), and `features/` (one folder per domain). UI must cover loading/empty/error/success/disabled/hover-focus states, be responsive, accessible, and support EN/ES + dark mode.
- **Frontend i18n & design system**: i18n is a **custom signal-based `LanguageService`** (`core/i18n/`), not ngx-translate or Angular's built-in i18n — strings live in `core/i18n/translations.ts` and are used via the `translate` pipe; default language is `es`, persisted to `localStorage`. The **quiet-luxury** brand (dark-mode default; ink/bone + a single pine accent; Cormorant Garamond display + Hanken Grotesk body) is defined in `tailwind.config.js` (palette/fonts/shadows) and `src/styles.css` (CSS vars + component classes like `.page-shell`, `.soft-card`, `.ui-button-primary`); theming is `ThemeService` toggling a `.dark` class on `<html>`, with a no-flash inline script in `index.html`. See `docs/design/quiet-luxury-redesign.md`.

### About AGENTS.md

`AGENTS.md` documents the original build order and rules. Several entries are **historical milestones now completed or superseded** — e.g. "Do not create microservices" and "do not touch frontend": the project has since intentionally moved to microservices + Kafka and has a full frontend. Treat its *coding rules* (layering, DTOs, validation, never trust client input) as current; treat its *sequencing/priority* sections as a snapshot of an earlier phase.

### The `vulnerable-lab` branch (do not "fix")

A dedicated `vulnerable-lab` branch (with `lab/0x` tags) **intentionally reintroduces** real vulnerabilities that `main` already fixes — IDOR, JWT role-claim trust, client-trusted prices — each with an exploit→remediation writeup in `docs/appsec/labs/` on that branch. The broken state **is** the teaching artifact: **never "fix" those vulnerabilities or merge `vulnerable-lab` into `main`.** The automated commit security scanner flags them by design — that is expected, not a regression.

## Local service ports

Gateway 8088 · Core 8080 · notification-service 8082 · Frontend 4200 · Postgres 5433 · Kafka 29092 (host) / 9092 (in-network) · Kafka UI 8081 · Redis 6379 · MinIO 9000/9001 · Mailpit UI 8025 / SMTP 1025.

## Production config to override

```powershell
$env:APP_SECURITY_JWT_SECRET="<>=32 char secret>"   # REQUIRED — the dev default is a known weak secret
# Access tokens are intentionally short (15 min default); refresh-token rotation extends sessions. Override only if needed:
$env:APP_SECURITY_JWT_EXPIRATION_MINUTES="15"
```
