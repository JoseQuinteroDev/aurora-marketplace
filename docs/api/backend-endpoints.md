# Backend Endpoints

Base URL: `http://localhost:8080`

Use `Authorization: Bearer <token>` for protected endpoints.

## Auth

- `POST /api/auth/register`
- `POST /api/auth/login`

## Public Catalog

- `GET /api/categories`
- `GET /api/brands`
- `GET /api/products`
- `GET /api/products/search?q=term`
- `GET /api/products/{slug}`

## Cart

- `GET /api/cart`
- `POST /api/cart/items`
- `PATCH /api/cart/items/{itemId}`
- `DELETE /api/cart/items/{itemId}`
- `DELETE /api/cart`
- `POST /api/cart/apply-coupon`
- `DELETE /api/cart/coupon`

## Wishlist

- `GET /api/wishlist`
- `POST /api/wishlist/{productId}`
- `DELETE /api/wishlist/{productId}`

## Reviews

- `GET /api/products/{productId}/reviews`
- `POST /api/products/{productId}/reviews`
- `DELETE /api/admin/reviews/{reviewId}`

## Checkout, Orders And Payments

- `POST /api/checkout/confirm`
- `GET /api/orders`
- `GET /api/orders/{id}`
- `POST /api/payments/{orderId}/simulate`

## Admin Catalog

- `POST /api/admin/categories`
- `PUT /api/admin/categories/{id}`
- `DELETE /api/admin/categories/{id}`
- `POST /api/admin/brands`
- `PUT /api/admin/brands/{id}`
- `DELETE /api/admin/brands/{id}`
- `POST /api/admin/products`
- `PUT /api/admin/products/{id}`
- `DELETE /api/admin/products/{id}`

## Admin Inventory

- `GET /api/admin/inventory`
- `GET /api/admin/inventory/{variantId}`
- `PATCH /api/admin/inventory/{variantId}`
- `GET /api/admin/inventory/movements`
- `POST /api/admin/inventory/{variantId}/adjust`

## Admin Orders, Coupons And Audit

- `GET /api/admin/orders`
- `GET /api/admin/orders/{id}`
- `PATCH /api/admin/orders/{id}/status`
- `GET /api/admin/coupons`
- `POST /api/admin/coupons`
- `PUT /api/admin/coupons/{id}`
- `DELETE /api/admin/coupons/{id}`
- `GET /api/admin/audit-logs`
- `GET /api/admin/dashboard/summary`

## Admin Batch

- `GET /api/admin/batch/jobs`
- `POST /api/admin/batch/jobs/{jobName}/run`
- `GET /api/admin/batch/executions`

Available job names:

- `importProductsJob`
- `syncInventoryJob`
- `cleanAbandonedCartsJob`
