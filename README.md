# Aurora Marketplace

Aurora Marketplace is a full-stack e-commerce platform built as a professional portfolio project.

The goal is not to build a simple online shop, but a real-world e-commerce system with a modern UI, secure backend, batch processing, admin dashboard and AppSec-focused documentation.

## Tech Stack

### Backend

- Java 21
- Spring Boot 3.5
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Spring Batch
- Spring Validation
- Spring Actuator
- Maven

### Frontend

- Angular
- TypeScript
- Tailwind CSS
- Modern UI/UX design system
- Responsive layouts
- Microinteractions
- Dark mode

### Infrastructure

- Docker Compose
- PostgreSQL
- Redis
- MinIO
- Mailpit

## Current Status

- Monorepo created.
- Backend generated with Spring Initializr.
- Java 21 configured.
- Spring Boot aligned to version 3.5.
- PostgreSQL, Redis, MinIO and Mailpit running with Docker Compose.
- Backend connected to PostgreSQL.
- Flyway enabled.
- Actuator enabled.

## Local Services

| Service | URL / Port |
|---|---|
| Backend | http://localhost:8080 |
| PostgreSQL | localhost:5433 |
| Redis | localhost:6379 |
| MinIO API | http://localhost:9000 |
| MinIO Console | http://localhost:9001 |
| Mailpit UI | http://localhost:8025 |
| Mailpit SMTP | localhost:1025 |

## Run Infrastructure

docker compose up -d

## Run Backend

cd backend
.\mvnw.cmd spring-boot:run

## Health Check

http://localhost:8080/actuator/health

## Planned Modules

- Auth
- User
- Catalog
- Product
- Category
- Inventory
- Cart
- Order
- Payment
- Review
- Wishlist
- Promotion
- Batch
- Admin
- Audit
- Notification
- Security
- Common API layer

## Security Vision

- Secure authentication
- Role-based authorization
- Input validation
- Ownership validation
- Secure error handling
- Audit logs
- OWASP Top 10 documentation
- Future vulnerable-lab branch for AppSec practice

## Batch Processing Vision

- Product import
- Inventory synchronization
- Abandoned cart cleanup
- Daily metrics calculation
- Expired token cleanup
- Recommendation ranking

## Roadmap

1. Backend common architecture.
2. Global API response model.
3. Global exception handling.
4. Security base configuration.
5. Auth module.
6. Catalog module.
7. Inventory module.
8. Cart module.
9. Checkout and orders.
10. Admin dashboard.
11. Spring Batch jobs.
12. Angular frontend.
13. Premium UI/UX design system.
14. AppSec lab.
15. Testing.
16. Deployment.
