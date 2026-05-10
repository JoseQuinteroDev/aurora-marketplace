package com.aurora.backend.order.dto;

import com.aurora.backend.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(
        @NotNull(message = "Order status is required.")
        OrderStatus status,

        @Size(max = 255, message = "Status note must be at most 255 characters.")
        String note
) {
}
