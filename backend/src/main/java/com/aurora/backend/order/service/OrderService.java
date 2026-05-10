package com.aurora.backend.order.service;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.order.dto.OrderResponse;
import com.aurora.backend.order.dto.UpdateOrderStatusRequest;
import com.aurora.backend.order.entity.Order;
import com.aurora.backend.order.entity.OrderStatusHistory;
import com.aurora.backend.order.repository.OrderRepository;
import com.aurora.backend.user.entity.User;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final AuditLogService auditLogService;

    public OrderService(OrderRepository orderRepository, AuditLogService auditLogService) {
        this.orderRepository = orderRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listUserOrders(User user) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getUserOrder(User user, UUID orderId) {
        return orderRepository.findByIdAndUserId(orderId, user.getId())
                .map(OrderResponse::from)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return OrderResponse.from(findOrder(orderId));
    }

    @Transactional
    public OrderResponse updateStatus(UUID orderId, UpdateOrderStatusRequest request, User actor) {
        Order order = findOrder(orderId);
        order.changeStatus(request.status());
        order.addStatusHistory(new OrderStatusHistory(
                request.status(),
                normalizeNullableText(request.note()),
                actor
        ));

        Order savedOrder = orderRepository.saveAndFlush(order);
        auditLogService.log(
                AuditEventType.ORDER_STATUS_CHANGED,
                actor,
                "ORDER",
                savedOrder.getId(),
                "Order status changed to " + request.status()
        );

        return OrderResponse.from(savedOrder);
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
