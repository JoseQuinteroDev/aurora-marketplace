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
- `AGENTS.md` — original working rules and package conventions (note: some are historical milestones; see below).

## Repository layout

| Path | What |
|---|---|
| `backend/` | Core commerce service (`com.aurora.backend`), Java 21 / Spring Boot 3.5 / Maven |
| `gateway/` | Spring Cloud Gateway — single entry point, routing, CORS, Resilience4j fallback |
| `services/notification-service/` | Event consumer → transactional email (`com.aurora.notification`) |
| `frontend/` | Angular 21 storefront + admin (`src/app`) |
| `docker-compose.yml` | Infra (default) and full app stack (`--profile apps`) |
| `docs/` | Architecture + API docs |

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
npm test                         # ng test (Karma)
```

## Key conventions and gotchas

- **Layered, modular packages** under `com.aurora.backend`: each domain (`auth`, `catalog`, `inventory`, `cart`, `checkout`, `order`, `payment`, `audit`, `batch`, `admin`) is a slice with its own `controller` / `service` / `dto` / `entity` / `repository`. Match this when adding features.
- **Controllers are thin** HTTP adapters; business rules + `@Transactional` live in services. **Never return JPA entities** — always map to DTOs. Use Bean Validation on inputs; errors flow through `common.exception.GlobalExceptionHandler` and wrap in `common.api` (`ApiResponse`/`ErrorResponse`).
- **Never trust client values** for prices, totals, stock, roles, ownership, or authorization — the backend recalculates them. This is a security-focused project.
- **Security**: stateless JWT Bearer. Subject = user email, role claim mapped to `ROLE_CUSTOMER` / `ROLE_ADMIN`. Public: auth, `/actuator/health`, public catalog/review reads. Everything under `/api/admin/**` requires `ROLE_ADMIN`. See `config/SecurityConfig.java` and `security/jwt/`.
- **Event publishing uses the Transactional Outbox pattern** — do NOT call Kafka directly from business code. In a `@Transactional` service, call `OutboxEventRecorder.record(...)` (`messaging/outbox/`); it writes an `event_outbox` row in the same DB transaction. The `@Scheduled OutboxRelay` drains PENDING rows to Kafka (`DomainEventPublisher` is the low-level sender) and marks them PUBLISHED, parking exhausted rows as FAILED. This guarantees at-least-once delivery with no dual-write. Topics live in `messaging/AuroraTopics`; disable with `app.events.enabled=false`. The producer serializes events to JSON strings (StringSerializer), so the relay sends raw JSON.
- **Consumers must be idempotent and resilient.** at-least-once delivery means redeliveries happen — the notification-service dedupes on `eventId` (`ProcessedEventTracker`, marked only after successful delivery) and uses a `DefaultErrorHandler` with exponential backoff + per-topic Dead Letter Topics (`<topic>.DLT`). Throw `NonRetryableEventException` for poison messages (immediate DLT); throw any other exception for transient failures (retried, then DLT). Each service owns its event classes — there is no shared library, and event records use `@JsonIgnoreProperties(ignoreUnknown = true)` so the contract can evolve.
- **Gateway resilience** (`gateway/application.yml`): per-client-IP Redis rate limiting (`RequestRateLimiter` + `clientIpKeyResolver`), `Retry` filter for GETs, Resilience4j circuit breaker + timeouts, JSON fallbacks. Routes are order-sensitive: `/api/notifications/**` is declared before the `/api/**` core catch-all. Requires Redis (part of the infra compose).
- **Observability**: all three services expose `/actuator/prometheus` and emit `traceId`/`spanId` in logs (Micrometer Tracing/Brave). When adding actuator endpoints to the core, remember they sit behind Spring Security — permit them in `SecurityConfig`.
- **Schema is Flyway-managed** (`backend/src/main/resources/db/migration/V*__*.sql`) with `ddl-auto: validate` — never rely on Hibernate to create/alter tables; add a new `V#` migration instead. Spring Batch owns its metadata tables; Aurora adds `batch_job_audit` for admin visibility.
- **Batch jobs** are tasklet-based (`importProductsJob`, `syncInventoryJob`, `cleanAbandonedCartsJob`); `spring.batch.job.enabled=false` (triggered via `/api/admin/batch/**`). CSV paths are env-overridable (`APP_BATCH_*`).
- **Gateway** routes `/api/**` → core, strips/rewrites actuator paths, and returns a JSON fallback (`/fallback/core`) via Resilience4j when the core is down.
- **Frontend** is standalone Angular 21 with Tailwind, organized as `core/` (i18n, models), `services/`, `guards/`, `interceptors/`, `layout/`, `shared/`, `design-system/`, and `features/` (one folder per domain). UI must cover loading/empty/error/success/disabled/hover-focus states, be responsive, accessible, and support EN/ES + dark mode.

### About AGENTS.md

`AGENTS.md` documents the original build order and rules. Several entries are **historical milestones now completed or superseded** — e.g. "Do not create microservices" and "do not touch frontend": the project has since intentionally moved to microservices + Kafka and has a full frontend. Treat its *coding rules* (layering, DTOs, validation, never trust client input) as current; treat its *sequencing/priority* sections as a snapshot of an earlier phase.

## Local service ports

Gateway 8088 · Core 8080 · notification-service 8082 · Frontend 4200 · Postgres 5433 · Kafka 29092 (host) / 9092 (in-network) · Kafka UI 8081 · Redis 6379 · MinIO 9000/9001 · Mailpit UI 8025 / SMTP 1025.

## Production config to override

```powershell
$env:APP_SECURITY_JWT_SECRET="<>=32 char secret>"
$env:APP_SECURITY_JWT_EXPIRATION_MINUTES="60"
```
