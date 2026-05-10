package com.aurora.backend.order.entity;

public enum OrderStatus {
    CREATED,
    PENDING_PAYMENT,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}
