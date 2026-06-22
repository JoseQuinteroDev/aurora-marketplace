package com.aurora.backend.order.controller;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.order.dto.OrderResponse;
import com.aurora.backend.order.service.OrderService;
import com.aurora.backend.security.CurrentUserService;
import com.aurora.backend.user.entity.User;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public OrderController(OrderService orderService, CurrentUserService currentUserService) {
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> listOrders(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Orders retrieved successfully.", orderService.listUserOrders(user));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(Authentication authentication, @PathVariable UUID id) {
        // LAB 01 — IDOR (OWASP A01). The ownership check is intentionally removed here.
        // main calls orderService.getUserOrder(user, id), which loads the order via
        // findByIdAndUserId(...) so a customer can only ever read their OWN order.
        // Here we authenticate the caller but then look the order up by id ALONE
        // (the unscoped lookup meant for admins), so any authenticated customer can
        // read any other customer's order just by guessing/altering the UUID.
        // See docs/appsec/labs/01-broken-access-control-idor.md
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Order retrieved successfully.", orderService.getOrder(id));
    }
}
