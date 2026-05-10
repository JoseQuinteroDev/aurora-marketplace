package com.aurora.backend.admin.dashboard.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.aurora.backend.admin.dashboard.dto.AdminDashboardSummaryResponse;
import com.aurora.backend.catalog.product.repository.ProductRepository;
import com.aurora.backend.inventory.repository.InventoryRepository;
import com.aurora.backend.order.repository.OrderRepository;
import com.aurora.backend.promotion.repository.CouponRepository;
import com.aurora.backend.review.repository.ReviewRepository;
import com.aurora.backend.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final CouponRepository couponRepository;
    private final ReviewRepository reviewRepository;

    public AdminDashboardService(
            ProductRepository productRepository,
            UserRepository userRepository,
            OrderRepository orderRepository,
            InventoryRepository inventoryRepository,
            CouponRepository couponRepository,
            ReviewRepository reviewRepository
    ) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.couponRepository = couponRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary() {
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        orderRepository.countOrdersByStatus()
                .forEach(row -> ordersByStatus.put(row.getStatus().name(), row.getTotal()));

        BigDecimal totalRevenuePaid = orderRepository.sumPaidRevenue();

        return new AdminDashboardSummaryResponse(
                productRepository.count(),
                productRepository.countByActiveTrue(),
                userRepository.count(),
                orderRepository.count(),
                ordersByStatus,
                totalRevenuePaid == null ? BigDecimal.ZERO : totalRevenuePaid,
                inventoryRepository.countLowStockItems(),
                couponRepository.count(),
                reviewRepository.count()
        );
    }
}
