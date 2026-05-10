package com.aurora.backend.order.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.order.entity.Order;
import com.aurora.backend.order.entity.OrderStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Order> findAllByOrderByCreatedAtDesc();

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByOrderNumber(String orderNumber);

    @Query("select coalesce(sum(o.total), 0) from Order o where o.status = com.aurora.backend.order.entity.OrderStatus.PAID")
    BigDecimal sumPaidRevenue();

    @Query("select o.status as status, count(o) as total from Order o group by o.status")
    List<OrderStatusCount> countOrdersByStatus();

    interface OrderStatusCount {
        OrderStatus getStatus();

        long getTotal();
    }
}
