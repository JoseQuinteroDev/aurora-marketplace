package com.aurora.backend.checkout.controller;

import com.aurora.backend.checkout.service.CheckoutService;
import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.order.dto.OrderResponse;
import com.aurora.backend.security.CurrentUserService;
import com.aurora.backend.user.entity.User;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final CurrentUserService currentUserService;

    public CheckoutController(CheckoutService checkoutService, CurrentUserService currentUserService) {
        this.checkoutService = checkoutService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/confirm")
    public ApiResponse<OrderResponse> confirmCheckout(
            Authentication authentication,
            // Optional safe-retry key (OWASP A04): the SPA sends a stable UUID per checkout
            // intent so a double-click / network retry resolves to one order, not two.
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success(
                "Checkout confirmed successfully.",
                checkoutService.confirmCheckout(user, idempotencyKey));
    }
}
