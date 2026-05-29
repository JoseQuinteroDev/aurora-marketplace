# Event-Driven Microservices

Aurora Marketplace is organized as an **event-driven platform** built around a
strong commerce core, an API gateway as the single entry point, and decoupled
microservices that react to domain events over Kafka.

This is a pragmatic decomposition: the transactional commerce domain (catalog,
cart, checkout, orders, payments) stays cohesive in one service that owns its
data, while genuinely independent concerns (notifications today; analytics,
fulfilment, search indexing tomorrow) are extracted as services that integrate
**only** through published events — never through a shared database or jar.

## Components

| Component | Tech | Port | Responsibility |
|---|---|---|---|
| API Gateway | Spring Cloud Gateway | 8088 | Single entry point, routing, CORS, circuit breaker + fallback |
| Core service (`backend`) | Spring Boot 3.5 | 8080 | Commerce domain; **publishes** domain events |
| notification-service | Spring Boot 3.5 | 8082 | **Consumes** events; sends transactional emails |
| Kafka | Apache Kafka 3.9 (KRaft) | 29092 | Event backbone |
| Kafka UI | provectus/kafka-ui | 8081 | Topic / event / consumer-group inspection |
| Storefront | Angular 21 | 4200 | Customer + admin UI (enters via the gateway) |

## Event Flow

```mermaid
flowchart LR
    UI[Angular storefront] -->|/api/**| GW[API Gateway :8088]
    GW -->|routes| CORE[Core service :8080]

    CORE -->|aurora.orders.created| K[(Kafka)]
    CORE -->|aurora.payments.confirmed| K
    CORE -->|aurora.payments.failed| K

    K --> NS[notification-service :8082]
    NS -->|SMTP| MP[Mailpit :1025]

    CORE --- PG[(PostgreSQL)]
```

## Topics (the contract)

The integration contract is the **topic name + JSON shape** of each event.
There is deliberately no shared library: producer and consumers each own their
own event classes, and consumers ignore unknown fields so the contract can
evolve safely.

| Topic | Producer | Consumers | Trigger |
|---|---|---|---|
| `aurora.orders.created` | core | notification-service | Checkout confirmed |
| `aurora.payments.confirmed` | core | notification-service | Payment succeeded |
| `aurora.payments.failed` | core | notification-service | Payment failed |

Event records carry an `eventId`, `occurredAt`, the order identifiers, the
customer email/name and the relevant monetary fields — enough context for a
consumer to act without calling back into the core.

## Design Principles

- **Resilience first.** Publishing is best-effort and fail-fast: if Kafka is
  down, checkout and payment still succeed; the producer logs a warning. It can
  be disabled entirely with `app.events.enabled=false`.
- **No shared state.** Services integrate only through events. Each service owns
  its data (notification-service keeps an in-memory log of what it processed).
- **Single entry point.** The gateway centralizes routing, CORS and resilience
  (Resilience4j circuit breaker with a JSON fallback when the core is down).
- **Independent deployability.** Each service has its own Maven build and
  Dockerfile and can be scaled or released on its own.

## Running

```powershell
# Infrastructure only (run apps locally with mvnw / ng):
docker compose up -d

# Full containerized stack (gateway + core + notification-service + Kafka):
docker compose --profile apps up -d --build
```

When running apps locally, point each one at the broker exposed on
`localhost:29092` (the default). Inside the Docker network they use `kafka:9092`.

## Observability

- **Kafka UI** at <http://localhost:8081> — inspect topics, events and the
  `aurora-notification-service` consumer group.
- **Mailpit** at <http://localhost:8025> — see the emails the notification
  service produced.
- **GET** `http://localhost:8082/api/notifications` — the notification log.
- **Gateway** health at <http://localhost:8088/actuator/health>.
