import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminOrder } from '../core/models/admin-order.model';
import { AdminOrderService } from './admin-order.service';

function order(overrides: Partial<AdminOrder> = {}): AdminOrder {
  return {
    id: 'o1',
    orderNumber: 'AUR-1001',
    status: 'PAID',
    couponCode: null,
    subtotal: 100,
    discountTotal: 0,
    total: 100,
    items: [],
    statusHistory: [],
    createdAt: '2026-06-28T10:00:00Z',
    updatedAt: '2026-06-28T10:00:00Z',
    ...overrides
  };
}

describe('AdminOrderService', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    return {
      service: TestBed.inject(AdminOrderService),
      http: TestBed.inject(HttpTestingController)
    };
  }

  it('listOrders GETs /api/admin/orders and unwraps the data array', () => {
    const { service, http } = setup();

    let result: AdminOrder[] | undefined;
    service.listOrders().subscribe((r) => (result = r));
    const req = http.expectOne('/api/admin/orders');
    expect(req.request.method).toBe('GET');
    req.flush({ data: [order(), order({ id: 'o2' })] });

    expect(result?.length).toBe(2);
    expect(result?.[0].orderNumber).toBe('AUR-1001');
    http.verify();
  });

  it('listOrders returns [] when data is null', () => {
    const { service, http } = setup();

    let result: AdminOrder[] | undefined;
    service.listOrders().subscribe((r) => (result = r));
    http.expectOne('/api/admin/orders').flush({ data: null });

    expect(result).toEqual([]);
    http.verify();
  });

  it('getOrder GETs /api/admin/orders/{id} and unwraps the order', () => {
    const { service, http } = setup();

    let result: AdminOrder | undefined;
    service.getOrder('o1').subscribe((r) => (result = r));
    const req = http.expectOne('/api/admin/orders/o1');
    expect(req.request.method).toBe('GET');
    req.flush({ data: order() });

    expect(result?.id).toBe('o1');
    http.verify();
  });

  it('updateStatus PATCHes /api/admin/orders/{id}/status with the body and unwraps the result', () => {
    const { service, http } = setup();

    let result: AdminOrder | undefined;
    service.updateStatus('o1', { status: 'SHIPPED', note: 'tracking added' }).subscribe((r) => (result = r));
    const req = http.expectOne('/api/admin/orders/o1/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'SHIPPED', note: 'tracking added' });
    req.flush({ data: order({ status: 'SHIPPED' }) });

    expect(result?.status).toBe('SHIPPED');
    http.verify();
  });

  it('updateStatus omits the note when not provided', () => {
    const { service, http } = setup();

    service.updateStatus('o1', { status: 'CANCELLED' }).subscribe();
    const req = http.expectOne('/api/admin/orders/o1/status');
    expect(req.request.body).toEqual({ status: 'CANCELLED' });
    req.flush({ data: order({ status: 'CANCELLED' }) });

    http.verify();
  });
});
