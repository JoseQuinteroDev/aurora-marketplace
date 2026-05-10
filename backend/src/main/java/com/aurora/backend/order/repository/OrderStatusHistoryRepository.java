package com.aurora.backend.order.repository;

import java.util.UUID;

import com.aurora.backend.order.entity.OrderStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
}
