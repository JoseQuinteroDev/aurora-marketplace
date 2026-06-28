# Contributing to Aurora Marketplace

Thanks for your interest. Aurora is a portfolio/AppSec project, but it is maintained like a
production codebase — contributions are welcome and held to that bar.

## Ground rules

- **Read [`CLAUDE.md`](CLAUDE.md) first.** It is the source of truth for architecture, conventions,
  and gotchas (layered slices, never return JPA entities, never trust client input, the transactional
  outbox, the no-shared-library event contract, the Docker-split test suite).
- **Security is first-class.** Never weaken a control without discussion. When you fix a
  security-relevant bug, add a regression test and catalog it in
  [`docs/appsec/security-testing.md`](docs/appsec/security-testing.md).
- **Do not "fix" the `vulnerable-lab` branch** — its vulnerabilities are intentional teaching artifacts.

## Local setup

```bash
docker compose up -d                 # infra: Postgres, Kafka, Redis, MinIO, Mailpit
cd backend && ./mvnw spring-boot:run # core on :8080  (repeat for gateway, notification-service)
cd frontend && npm install && npm start
```

## Before you open a PR

| Module | Build + test |
|---|---|
| backend / gateway / notification-service | `./mvnw clean install` (per module dir) |
| frontend | `npm run build && npm test` |

- Keep commits small and logically scoped; write a clear message (Conventional Commits style).
- New backend code: prefer Docker-free Mockito unit tests + `@WebMvcTest` slices (see CLAUDE.md).
- New endpoints: add Bean Validation, map errors through `GlobalExceptionHandler`, return DTOs (never entities),
  and document them (OpenAPI annotations + `docs/api/backend-endpoints.md`).
- CI (`ci.yml`) and the DevSecOps gates (`security.yml` — CodeQL, Trivy, **Gitleaks hard gate**, SBOM,
  dependency-review) must be green.

## Reporting a vulnerability

Do **not** open a public issue for security problems. Follow the private disclosure process in
[`SECURITY.md`](SECURITY.md).
