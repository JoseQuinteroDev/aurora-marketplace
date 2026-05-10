package com.aurora.backend.order.dto;

import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.order.entity.OrderStatus;
import com.aurora.backend.order.entity.OrderStatusHistory;

public record OrderStatusHistoryResponse(
        UUID id,
        OrderStatus status,
        String note,
        UUID changedByUserId,
        Instant createdAt
) {

    public static OrderStatusHistoryResponse from(OrderStatusHistory history) {
        UUID changedByUserId = history.getChangedBy() == null ? null : history.getChangedBy().getId();
        return new OrderStatusHistoryResponse(
                history.getId(),
                history.getStatus(),
                history.getNote(),
                changedByUserId,
                history.getCreatedAt()
        );
    }
}
