package com.aurora.backend.admin.dashboard.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AdminDashboardSummaryResponse(
        long totalProducts,
        long activeProducts,
        long totalUsers,
        long totalOrders,
        Map<String, Long> ordersByStatus,
        BigDecimal totalRevenuePaid,
        long lowStockItems,
        long totalCoupons,
        long totalReviews
) {
}
