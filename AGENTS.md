# AGENTS.md - Aurora Marketplace

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
12. Do not create microservices.
13. Do not implement JWT until the base common architecture is ready.
14. Do not touch frontend until backend common architecture is done.

## Backend package style

Use this structure under com.aurora.backend:

- common.api
- common.exception
- common.validation
- config
- security
- auth
- user
- catalog
- inventory
- cart
- order
- payment
- batch
- audit
- notification

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
