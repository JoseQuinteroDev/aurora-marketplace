import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminCoupon, AdminCouponRequest } from '../core/models/admin-coupon.model';
import { AdminCouponService } from './admin-coupon.service';

function coupon(overrides: Partial<AdminCoupon> = {}): AdminCoupon {
  return {
    id: 'c1',
    code: 'SUMMER10',
    type: 'PERCENTAGE',
    value: 10,
    active: true,
    startsAt: null,
    endsAt: null,
    maxUses: null,
    maxUsesPerUser: null,
    minimumOrderAmount: null,
    ...overrides
  };
}

const request: AdminCouponRequest = {
  code: 'SUMMER10',
  type: 'PERCENTAGE',
  value: 10,
  active: true,
  startsAt: null,
  endsAt: null,
  maxUses: 100,
  maxUsesPerUser: 1,
  minimumOrderAmount: 25
};

describe('AdminCouponService', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    return {
      service: TestBed.inject(AdminCouponService),
      http: TestBed.inject(HttpTestingController)
    };
  }

  it('listCoupons GETs /api/admin/coupons and unwraps the data array', () => {
    const { service, http } = setup();

    let result: AdminCoupon[] | undefined;
    service.listCoupons().subscribe((r) => (result = r));
    const req = http.expectOne('/api/admin/coupons');
    expect(req.request.method).toBe('GET');
    req.flush({ data: [coupon(), coupon({ id: 'c2', active: false })] });

    expect(result?.length).toBe(2);
    expect(result?.[1].active).toBe(false);
    http.verify();
  });

  it('listCoupons returns [] when data is null', () => {
    const { service, http } = setup();

    let result: AdminCoupon[] | undefined;
    service.listCoupons().subscribe((r) => (result = r));
    http.expectOne('/api/admin/coupons').flush({ data: null });

    expect(result).toEqual([]);
    http.verify();
  });

  it('createCoupon POSTs /api/admin/coupons with the request body', () => {
    const { service, http } = setup();

    let result: AdminCoupon | undefined;
    service.createCoupon(request).subscribe((r) => (result = r));
    const req = http.expectOne('/api/admin/coupons');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ data: coupon() });

    expect(result?.code).toBe('SUMMER10');
    http.verify();
  });

  it('updateCoupon PUTs /api/admin/coupons/{id} with the request body', () => {
    const { service, http } = setup();

    service.updateCoupon('c1', request).subscribe();
    const req = http.expectOne('/api/admin/coupons/c1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ data: coupon() });

    http.verify();
  });

  it('deleteCoupon DELETEs /api/admin/coupons/{id}', () => {
    const { service, http } = setup();

    service.deleteCoupon('c1').subscribe();
    const req = http.expectOne('/api/admin/coupons/c1');
    expect(req.request.method).toBe('DELETE');
    req.flush({ data: null });

    http.verify();
  });
});
