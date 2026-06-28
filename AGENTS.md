# AGENTS.md - Aurora Marketplace

> **⚠️ HISTORICAL — read CLAUDE.md for the current source of truth.**
>
> This file documents the project's **original build order and rules**. It is kept
> for history; several of its rules are now **SUPERSEDED** because the project has
> since intentionally grown past that early phase. In particular, these rules are
> **obsolete — do not follow them**:
>
> - ~~"Do not create microservices"~~ — the project is now event-driven with Kafka
>   and a `notification-service` microservice.
> - ~~"Do not implement JWT until the base common architecture is ready"~~ — stateless
>   JWT auth (plus refresh-token rotation, lockout, denylist) is shipped.
> - ~~"Do not touch frontend until backend common architecture is done"~~ — there is a
>   full Angular 21 storefront + admin.
>
> The **coding rules** below (incremental work, layering, DTOs vs entities, Bean
> Validation, global exception handling, never-trust-client-input) remain **current**.
> Treat the *sequencing / priority* sections as a snapshot of an earlier phase.
> See **`CLAUDE.md`** for the authoritative, up-to-date guidance.

## Project

Aurora Marketplace is a professional full-stack e-commerce portfolio project.

## Core stack

- Backend: Java 21, Spring Boot 3.5, Maven, PostgreSQL, Flyway, Spring Security, Spring Batch, Actuator.
- Frontend: Angular, TypeScript, Tailwind CSS, premium UI/UX, responsive design, dark mode and microinteractions.
- Infra: Docker Compose, PostgreSQL, Redis, MinIO, Mailpit.
- Security: AppSec, OWASP, validation, authorization, audit logs and future vulnerable-lab branch.

## Working rules

1. Work incrementally.
2. Do not make massive unrelated changes.
3. Before editing, explain the plan.
4. After editing, explain touched files and test commands.
5. Do not mix JPA entities with DTOs.
6. Do not put business logic in controllers.
7. Use modular architecture.
8. Use Bean Validation for input validation.
9. Use global exception handling.
10. Never trust frontend values for prices, roles, ownership, stock or authorization.
11. Keep the project ready for tests and documentation.
12. ~~Do not create microservices.~~ **(SUPERSEDED — the project is now event-driven with Kafka + `notification-service`.)**
13. ~~Do not implement JWT until the base common architecture is ready.~~ **(SUPERSEDED — JWT auth is shipped.)**
14. ~~Do not touch frontend until backend common architecture is done.~~ **(SUPERSEDED — a full Angular frontend exists.)**

## Backend package style

Use this layered, modular structure under com.aurora.backend. Each domain slice has
its own `controller` / `service` / `dto` / `entity` / `repository`. (Updated to the
current set of slices — the original list predates several domains.)

Domain slices:

- auth
- catalog
- inventory
- cart
- checkout
- order
- payment
- promotion (coupons)
- review
- wishlist
- audit
- batch
- admin

Cross-cutting packages:

- user (the `User` entity/repository, shared by `auth`)
- security (JWT + token services)
- messaging (transactional outbox + Kafka)
- common (`common.api`, `common.exception`, `common.validation`)
- config

Note: there is **no** `notification` package in the core — notification is a separate
microservice (`services/notification-service`, `com.aurora.notification`).

## UI/UX rules

When working on frontend, use UI UX Pro Max as design guidance.

Frontend must feel premium, modern and professional, inspired by Amazon, Apple Store, Shopify and Stripe Dashboard.

Every major UI must consider:

- loading state
- empty state
- error state
- success state
- disabled state
- hover/focus states
- mobile responsive design
- accessibility
- clean spacing
- visual hierarchy
- microinteractions

## Current priority

Current priority is backend common architecture:

- ApiResponse
- ErrorResponse
- BusinessException
- NotFoundException
- GlobalExceptionHandler
- temporary SecurityConfig

Goal:

- /actuator/health must be public.
- Other endpoints can remain protected until real auth is implemented.
- Validation and business errors must return clean JSON.

## Test commands

Use these commands after backend changes:

cd backend
.\\mvnw.cmd clean install -DskipTests
.\\mvnw.cmd spring-boot:run
