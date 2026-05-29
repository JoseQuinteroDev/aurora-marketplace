# Backend Architecture

Aurora Marketplace backend is a modular Spring Boot service that owns the commerce domain. It started as a self-contained monolith and now also acts as the **core service** in an event-driven landscape: it publishes domain events to Kafka that decoupled microservices (e.g. notification-service) consume. See [03_event_driven_microservices.md](03_event_driven_microservices.md) for the platform view.

## Core Principles

- Controllers are thin HTTP adapters.
- Services own business rules and transactions.
- DTOs are used for all request and response bodies.
- JPA entities are never returned directly.
- Bean Validation handles input validation.
- `GlobalExceptionHandler` returns clean JSON errors.
- Security is stateless with JWT Bearer tokens.
- Admin endpoints are protected with `ROLE_ADMIN`.
- Backend recalculates prices, totals, stock and discounts.

## Package Layout

- `common.api`: shared API response wrapper.
- `common.exception`: global errors and business exceptions.
- `config`: Spring Security and app configuration.
- `security`: current user and JWT support.
- `auth`: register/login.
- `user`: user entity, repository and roles.
- `catalog`: categories, brands, products, variants and images.
- `inventory`: stock and stock movements.
- `cart`: user cart.
- `wishlist`: customer wishlist.
- `review`: product reviews.
- `promotion`: coupons and coupon usage.
- `checkout`: cart-to-order checkout flow.
- `order`: orders, order items and status history.
- `payment`: simulated payment.
- `audit`: admin audit log.
- `batch`: Spring Batch jobs and admin batch API.

## Security Model

Public endpoints include auth, health, public catalog and public review reads. Customer operations require a valid JWT. Admin operations live under `/api/admin/**` and require `ROLE_ADMIN`.

JWT contains the user email as subject and role as a claim. Spring Security reloads the user from the database and maps the role to `ROLE_CUSTOMER` or `ROLE_ADMIN`.

## Checkout Flow

1. Customer adds active variants to cart.
2. Backend computes prices from products/variants.
3. Checkout validates the cart is not empty.
4. Checkout validates active products and available stock.
5. Coupon is revalidated if applied.
6. Stock is deducted and a `SALE` stock movement is created.
7. Order, order items and initial status history are created.
8. A pending simulated payment is created.
9. Coupon usage and audit logs are recorded.
10. Cart is cleared.
11. An `aurora.orders.created` event is **recorded to the transactional outbox**
    in the same transaction (see below).

Payment confirmation/failure record `aurora.payments.confirmed` /
`aurora.payments.failed` to the outbox.

## Transactional Outbox

Events are never sent to Kafka directly from the commerce flow. Instead
`OutboxEventRecorder` writes them to an `event_outbox` table **inside the same
database transaction** as the order/payment change. A scheduled `OutboxRelay`
then drains PENDING rows to Kafka and marks them PUBLISHED.

This removes the *dual-write problem*: an event exists if and only if its
business transaction committed (no phantom events on rollback, no lost events on
a broker outage — they stay PENDING until Kafka is reachable). The relay's claim
query uses `FOR UPDATE SKIP LOCKED`, so several core instances can relay
concurrently without producing duplicates. Delivery is therefore **at-least-once**;
consumers must be idempotent. Recording can be disabled with
`app.events.enabled=false`.

## Batch v1

Batch jobs are simple tasklet-based jobs:

- `importProductsJob`: imports catalog rows from CSV.
- `syncInventoryJob`: updates inventory from CSV.
- `cleanAbandonedCartsJob`: deletes old empty carts.

The app uses Spring Batch metadata plus a lightweight `batch_job_audit` table for admin visibility.
