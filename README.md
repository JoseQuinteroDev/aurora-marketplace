# Aurora Marketplace

Aurora Marketplace is a professional full-stack e-commerce portfolio project.

The goal is not to build a simple online shop, but a real-world e-commerce system with secure backend architecture, catalog, cart, checkout, admin tools, batch processing and AppSec-focused documentation.

## Tech Stack

### Backend

- Java 21
- Spring Boot 3.5
- Spring Web
- Spring Security
- JWT authentication
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
- Premium responsive UI/UX planned after backend MVP

### Infrastructure

- Docker Compose
- PostgreSQL
- Redis
- MinIO
- Mailpit

## Current Backend Status

Implemented MVP backend modules:

- Common API and error contracts.
- Global exception handling.
- JWT auth with register/login.
- Users with `CUSTOMER` and `ADMIN` roles.
- Catalog: categories, brands, products, variants and images.
- Inventory with stock movements.
- Cart, wishlist, reviews and coupons.
- Checkout from cart.
- Orders and order status history.
- Simulated payments.
- Audit logs.
- Admin dashboard summary.
- Admin inventory management.
- Spring Batch v1 jobs.

Not implemented yet:

- Angular frontend.
- Real payment provider such as Stripe.
- Refresh tokens.
- Email verification.
- Password recovery.
- Orders shipping integrations.

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

```powershell
docker compose up -d
```

## Run Backend

```powershell
cd backend
.\mvnw.cmd clean install -DskipTests
.\mvnw.cmd spring-boot:run
```

## Configuration

JWT development defaults exist in `application.yml`. In production, override at least:

```powershell
$env:APP_SECURITY_JWT_SECRET="replace-with-a-real-secret-of-at-least-32-chars"
$env:APP_SECURITY_JWT_EXPIRATION_MINUTES="60"
```

Batch file locations can be overridden:

```powershell
$env:APP_BATCH_IMPORT_PRODUCTS_FILE="data/import/products.csv"
$env:APP_BATCH_SYNC_INVENTORY_FILE="data/import/inventory.csv"
$env:APP_BATCH_ABANDONED_CART_RETENTION_HOURS="24"
```

## Health Check

```text
GET http://localhost:8080/actuator/health
```

## Main Backend Endpoints

Public:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/categories`
- `GET /api/brands`
- `GET /api/products`
- `GET /api/products/search?q=term`
- `GET /api/products/{slug}`
- `GET /api/products/{productId}/reviews`
- `GET /actuator/health`

Customer:

- `GET /api/cart`
- `POST /api/cart/items`
- `POST /api/cart/apply-coupon`
- `POST /api/checkout/confirm`
- `GET /api/orders`
- `POST /api/payments/{orderId}/simulate`
- `GET /api/wishlist`
- `POST /api/products/{productId}/reviews`

Admin:

- `/api/admin/categories/**`
- `/api/admin/brands/**`
- `/api/admin/products/**`
- `/api/admin/inventory/**`
- `/api/admin/orders/**`
- `/api/admin/coupons/**`
- `/api/admin/reviews/**`
- `/api/admin/audit-logs`
- `/api/admin/dashboard/summary`
- `/api/admin/batch/**`

More detail: `docs/api/backend-endpoints.md`.

## Batch Jobs

- `importProductsJob`: imports catalog data from CSV.
- `syncInventoryJob`: updates inventory by SKU from CSV.
- `cleanAbandonedCartsJob`: removes old empty carts.

Batch CSV files are local and configurable through environment variables. Spring Batch metadata tables are managed by Spring Batch; Aurora also stores simplified job audit rows in `batch_job_audit`.
