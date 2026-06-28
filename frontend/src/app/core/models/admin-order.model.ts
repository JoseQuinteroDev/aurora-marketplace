import { OrderStatus } from './order.model';

/** Re-export so admin code can import the union from one place. */
export type { OrderStatus } from './order.model';

export interface AdminOrderItem {
  id: string;
  productId: string;
  variantId: string;
  productName: string;
  productSlug: string;
  variantSku: string;
  variantName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface AdminOrderStatusHistory {
  id: string;
  status: OrderStatus;
  note: string | null;
  changedByUserId: string | null;
  createdAt: string;
}

export interface AdminOrder {
  id: string;
  orderNumber: string;
  status: OrderStatus;
  couponCode: string | null;
  subtotal: number;
  discountTotal: number;
  total: number;
  items: AdminOrderItem[];
  statusHistory: AdminOrderStatusHistory[];
  createdAt: string;
  updatedAt: string;
}

/** Body for PATCH /api/admin/orders/{id}/status. */
export interface AdminOrderStatusUpdate {
  status: OrderStatus;
  note?: string;
}

/**
 * The only legal status moves the backend accepts, keyed by the order's CURRENT status.
 * The current status is intentionally NOT a valid target. Terminal states map to `[]`.
 * Mirrors the server-side transition gate so the UI never offers an illegal move.
 */
export const ALLOWED_ORDER_TRANSITIONS: Record<OrderStatus, readonly OrderStatus[]> = {
  CREATED: ['PENDING_PAYMENT', 'PAID', 'CANCELLED'],
  PENDING_PAYMENT: ['PAID', 'CANCELLED'],
  PAID: ['PREPARING', 'SHIPPED', 'CANCELLED', 'REFUNDED'],
  PREPARING: ['SHIPPED', 'CANCELLED', 'REFUNDED'],
  SHIPPED: ['DELIVERED', 'REFUNDED'],
  DELIVERED: ['REFUNDED'],
  CANCELLED: [],
  REFUNDED: []
};

/** The legal next statuses for a given current status (empty for terminal states). */
export function allowedTransitions(current: OrderStatus): readonly OrderStatus[] {
  return ALLOWED_ORDER_TRANSITIONS[current];
}
