package com.aurora.backend.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.order.entity.Order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Order> findAllByOrderByCreatedAtDesc();

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByOrderNumber(String orderNumber);
}
