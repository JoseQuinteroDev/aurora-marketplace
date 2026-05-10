package com.aurora.backend.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aurora.backend.order.entity.Order;
import com.aurora.backend.order.entity.OrderStatus;

public record OrderResponse(
        UUID id,
        String orderNumber,
        OrderStatus status,
        String couponCode,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal total,
        List<OrderItemResponse> items,
        List<OrderStatusHistoryResponse> statusHistory,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getCouponCode(),
                order.getSubtotal(),
                order.getDiscountTotal(),
                order.getTotal(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getStatusHistory().stream().map(OrderStatusHistoryResponse::from).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
