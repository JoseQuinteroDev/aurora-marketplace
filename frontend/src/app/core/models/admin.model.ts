export interface AdminDashboardSummary {
  totalProducts: number;
  activeProducts: number;
  totalUsers: number;
  totalOrders: number;
  ordersByStatus: Record<string, number>;
  totalRevenuePaid: number;
  lowStockItems: number;
  totalCoupons: number;
  totalReviews: number;
}
