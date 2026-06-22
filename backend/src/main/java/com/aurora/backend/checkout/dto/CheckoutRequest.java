package com.aurora.backend.checkout.dto;

import java.math.BigDecimal;

/**
 * LAB 03 — checkout request body (OWASP A04).
 *
 * <p>This DTO does not exist on {@code main}: there, {@code POST /api/checkout/confirm}
 * takes <b>no body</b> and the server recomputes the total from the cart. Accepting a
 * client-supplied {@code clientTotal} — and trusting it — is the vulnerability this lab
 * demonstrates. See {@code docs/appsec/labs/03-trusting-client-prices.md}.
 */
public record CheckoutRequest(BigDecimal clientTotal) {
}
