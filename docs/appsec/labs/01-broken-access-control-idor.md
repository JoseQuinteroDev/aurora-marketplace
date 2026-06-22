# Lab 01 — IDOR: reading another customer's order

- **OWASP:** A01 – Broken Access Control
- **Severity (CVSS-ish):** High — `AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N` (~7.7). Any
  logged-in customer can read any other customer's order data; no integrity or
  availability impact, but a full confidentiality break on order PII.
- **Affected component:** core (`backend`)
- **Control normally enforcing this:** per-resource ownership check in
  `OrderService.getUserOrder(...)` — see
  [`security-controls.md`](../security-controls.md) and the threat model's A01
  entry.

## 1. The weakness

The customer-facing endpoint `GET /api/orders/{id}` is meant to return **only the
caller's own** order. On `main`, the controller delegates to a user-scoped
service method:

```java
// main — OrderController#getOrder
User user = currentUserService.getCurrentUser(authentication);
return ApiResponse.success(..., orderService.getUserOrder(user, id));
```

```java
// main — OrderService#getUserOrder  (the control)
return orderRepository.findByIdAndUserId(orderId, user.getId())   // <-- scoped by owner
        .map(OrderResponse::from)
        .orElseThrow(() -> new NotFoundException("Order", orderId));
```

The lab commit (`lab/01`) keeps the authentication but swaps the lookup for the
**unscoped** method that exists for admins:

```java
// vulnerable-lab — OrderController#getOrder
User user = currentUserService.getCurrentUser(authentication);   // authenticated…
return ApiResponse.success(..., orderService.getOrder(id));       // …but not authorized
```

`OrderService.getOrder(id)` resolves the order by **id alone**
(`orderRepository.findById(orderId)`), with no ownership predicate. The endpoint
is still behind authentication (`anyRequest().authenticated()` in
`SecurityConfig`), so it *looks* protected — but authentication is not
authorization. The object reference (`id`) is now **direct** and **unchecked**:
a textbook **Insecure Direct Object Reference**.

> Diff it: `git diff main -- backend/.../order/controller/OrderController.java`

## 2. Exploit walkthrough

Two customers, the local stack up (`docker compose up -d`, core running, see the
[labs README](README.md)). All requests go through the gateway on `:8088`.

```bash
# 1) Victim — register, log in, place an order.
curl -s -X POST http://localhost:8088/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Vic","lastName":"Tim","email":"victim@aurora.test","password":"Password123!"}'

VICTIM=$(curl -s -X POST http://localhost:8088/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"victim@aurora.test","password":"Password123!"}' \
  | jq -r '.data.token')

# (add an item to the cart, then confirm checkout to create the order)
curl -s -X POST http://localhost:8088/api/checkout/confirm \
  -H "Authorization: Bearer $VICTIM" | jq '.data.id'
# -> note the returned order UUID, e.g. 3f9a... call it $VICTIM_ORDER_ID

# 2) Attacker — a *different*, ordinary customer account.
curl -s -X POST http://localhost:8088/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Mal","lastName":"Ory","email":"attacker@aurora.test","password":"Password123!"}'

ATTACKER=$(curl -s -X POST http://localhost:8088/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"attacker@aurora.test","password":"Password123!"}' \
  | jq -r '.data.token')

# 3) The attack — attacker reads the VICTIM's order by id.
curl -s http://localhost:8088/api/orders/$VICTIM_ORDER_ID \
  -H "Authorization: Bearer $ATTACKER" | jq
```

**Observed result (vulnerable-lab):** `200 OK` with the victim's full order —
order number, line items, SKUs, quantities, prices, totals, and status history.
The attacker never owned this resource and was never an admin.

Order ids are UUIDs (not enumerable 1,2,3…), so this is not a trivial sequential
sweep — but UUIDs leak (shared invoices, support tickets, referer headers, logs)
and "hard to guess" is not an access-control boundary. The control must be
*ownership*, not *obscurity*.

## 3. Impact

- **Asset hit:** customer order data (PII + purchase history) — a top asset in
  [`threat-model.md`](../threat-model.md).
- **Blast radius:** every order in the system, for any authenticated attacker.
  The same anti-pattern, if copied to cart/review/payment endpoints, widens it to
  those resources.
- A real-world IDOR on order data is a reportable data breach.

## 4. Detection

- **Audit/access pattern:** one account fetching orders whose `user_id` ≠ the
  caller's id is anomalous; an access log enriched with the resolved owner would
  surface it. Today Aurora audits *writes* (`ORDER_STATUS_CHANGED`) but not
  customer reads — a gap worth noting for production.
- **Volume signal:** a single token requesting many distinct order ids in a short
  window (id-spraying) is detectable at the gateway/metrics layer.
- **Trace:** each request carries a `traceId`; correlating principal vs. resource
  owner per request is the cleanest detection.

## 5. Remediation (already on `main`)

`main` scopes the read to the owner at the **query** level — the safest place,
because it cannot be bypassed by forgetting a guard clause:

```java
// OrderService#getUserOrder
orderRepository.findByIdAndUserId(orderId, user.getId())
        .orElseThrow(() -> new NotFoundException("Order", orderId));
```

A non-owner gets `404 Not Found` (not `403`) — deliberately, so the endpoint does
not even confirm the id exists for someone else. Re-running step 3 against `main`:

```
HTTP/1.1 404 Not Found
{"code":"NOT_FOUND","message":"Order ... was not found.", ...}
```

The exploit fails closed. Restore the control with
`git checkout main -- backend/.../order/controller/OrderController.java`.

## 6. Regression test

**Honest status: this is the gap.** [`security-testing.md`](../security-testing.md)
§2 names IDOR ("customer A cannot read customer B's order by id") as *"the single
most valuable test class to add next,"* and it does not exist yet. The unscoped
lookup is only caught today by the manual A01 checklist item, not by an automated
test — which is exactly how this regression could slip back in.

**Locking it in** (the test to add, integration-style with `spring-security-test`
+ Testcontainers PostgreSQL):

```java
// OrderAccessControlTest (proposed)
@Test
void customerCannotReadAnotherCustomersOrderById() throws Exception {
    UUID victimsOrder = seedOrderOwnedBy(victim);

    mockMvc.perform(get("/api/orders/{id}", victimsOrder)
                    .with(jwt(attacker)))          // a different ROLE_CUSTOMER
           .andExpect(status().isNotFound());      // never 200 with the data
}
```

This is also the bridge to the **"checkout/payment + authz test layer"** track:
adding it closes the highest-value coverage gap the threat model calls out.
