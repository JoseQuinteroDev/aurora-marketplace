package com.aurora.backend.payment.controller;

import java.util.UUID;

import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.payment.dto.PaymentResponse;
import com.aurora.backend.payment.dto.PaymentSimulationRequest;
import com.aurora.backend.payment.service.PaymentService;
import com.aurora.backend.security.CurrentUserService;
import com.aurora.backend.user.entity.User;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    public PaymentController(PaymentService paymentService, CurrentUserService currentUserService) {
        this.paymentService = paymentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{orderId}/simulate")
    public ApiResponse<PaymentResponse> simulatePayment(
            Authentication authentication,
            @PathVariable UUID orderId,
            @Valid @RequestBody PaymentSimulationRequest request
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Payment simulation completed.", paymentService.simulatePayment(user, orderId, request));
    }
}
