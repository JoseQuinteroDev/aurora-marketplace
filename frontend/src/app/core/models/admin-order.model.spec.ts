import { OrderStatus } from './order.model';
import { ALLOWED_ORDER_TRANSITIONS, allowedTransitions } from './admin-order.model';

const ALL_STATUSES: OrderStatus[] = [
  'CREATED',
  'PENDING_PAYMENT',
  'PAID',
  'PREPARING',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
  'REFUNDED'
];

/**
 * The order-detail status control must offer ONLY the legal next statuses for the
 * order's current status. These tests lock the gate to the backend's transition rules.
 */
describe('admin order legal transitions', () => {
  it('offers the exact legal targets per current status', () => {
    expect(allowedTransitions('CREATED')).toEqual(['PENDING_PAYMENT', 'PAID', 'CANCELLED']);
    expect(allowedTransitions('PENDING_PAYMENT')).toEqual(['PAID', 'CANCELLED']);
    expect(allowedTransitions('PAID')).toEqual(['PREPARING', 'SHIPPED', 'CANCELLED', 'REFUNDED']);
    expect(allowedTransitions('PREPARING')).toEqual(['SHIPPED', 'CANCELLED', 'REFUNDED']);
    expect(allowedTransitions('SHIPPED')).toEqual(['DELIVERED', 'REFUNDED']);
    expect(allowedTransitions('DELIVERED')).toEqual(['REFUNDED']);
  });

  it('treats CANCELLED and REFUNDED as terminal (no targets)', () => {
    expect(allowedTransitions('CANCELLED')).toEqual([]);
    expect(allowedTransitions('REFUNDED')).toEqual([]);
  });

  it('never offers the current status itself as a target', () => {
    for (const status of ALL_STATUSES) {
      expect(allowedTransitions(status)).not.toContain(status);
    }
  });

  it('only ever offers valid OrderStatus values', () => {
    for (const status of ALL_STATUSES) {
      for (const target of allowedTransitions(status)) {
        expect(ALL_STATUSES).toContain(target);
      }
    }
  });

  it('defines a transition list for every status', () => {
    for (const status of ALL_STATUSES) {
      expect(ALLOWED_ORDER_TRANSITIONS[status]).toBeDefined();
    }
  });
});
