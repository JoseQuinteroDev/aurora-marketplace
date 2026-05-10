package com.aurora.backend.payment.service;

import java.util.UUID;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.order.entity.Order;
import com.aurora.backend.order.entity.OrderStatus;
import com.aurora.backend.order.entity.OrderStatusHistory;
import com.aurora.backend.order.repository.OrderRepository;
import com.aurora.backend.payment.dto.PaymentResponse;
import com.aurora.backend.payment.dto.PaymentSimulationRequest;
import com.aurora.backend.payment.entity.Payment;
import com.aurora.backend.payment.entity.PaymentAttempt;
import com.aurora.backend.payment.entity.PaymentMethod;
import com.aurora.backend.payment.entity.PaymentStatus;
import com.aurora.backend.payment.repository.PaymentRepository;
import com.aurora.backend.user.entity.User;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogService auditLogService;

    public PaymentService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            AuditLogService auditLogService
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PaymentResponse simulatePayment(User user, UUID orderId, PaymentSimulationRequest request) {
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new NotFoundException("Order", orderId));

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "ORDER_NOT_PAYABLE",
                    "Order cannot be paid in its current status."
            );
        }

        if (order.getStatus() == OrderStatus.PAID && Boolean.TRUE.equals(request.success())) {
            throw new BusinessException(HttpStatus.CONFLICT, "ORDER_ALREADY_PAID", "Order is already paid.");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseGet(() -> paymentRepository.save(new Payment(
                        order,
                        PaymentStatus.PENDING,
                        PaymentMethod.SIMULATED_CARD,
                        order.getTotal()
                )));

        if (Boolean.TRUE.equals(request.success())) {
            payment.markPaid();
            payment.addAttempt(new PaymentAttempt(true, PaymentStatus.PAID, normalizeMessage(request.message(), "Payment simulated successfully.")));
            order.changeStatus(OrderStatus.PAID);
            order.addStatusHistory(new OrderStatusHistory(OrderStatus.PAID, "Payment simulated successfully.", user));

            auditLogService.log(
                    AuditEventType.PAYMENT_SIMULATED_SUCCESS,
                    user,
                    "ORDER",
                    order.getId(),
                    "Simulated payment succeeded for order " + order.getOrderNumber()
            );
        } else {
            payment.markFailed();
            payment.addAttempt(new PaymentAttempt(false, PaymentStatus.FAILED, normalizeMessage(request.message(), "Payment simulation failed.")));

            if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                order.changeStatus(OrderStatus.PENDING_PAYMENT);
                order.addStatusHistory(new OrderStatusHistory(OrderStatus.PENDING_PAYMENT, "Payment simulation failed.", user));
            }

            auditLogService.log(
                    AuditEventType.PAYMENT_SIMULATED_FAILED,
                    user,
                    "ORDER",
                    order.getId(),
                    "Simulated payment failed for order " + order.getOrderNumber()
            );
        }

        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        return PaymentResponse.from(savedPayment);
    }

    private String normalizeMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }

        return message.trim();
    }
}
