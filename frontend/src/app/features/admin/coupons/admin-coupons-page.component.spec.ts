import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { AdminCoupon } from '../../../core/models/admin-coupon.model';
import { AdminCouponService } from '../../../services/admin-coupon.service';
import { ToastService } from '../../../services/toast.service';
import { AdminCouponsPageComponent } from './admin-coupons-page.component';

/**
 * Coupon create/edit form — exercised at the component-logic level (no full render).
 * A fake AdminCouponService captures the request bodies and feeds the list; ToastService
 * is a no-op. The constructor calls load(), resolved synchronously by the fake.
 */
describe('AdminCouponsPageComponent', () => {
  let listResult: () => Observable<AdminCoupon[]>;
  let createCalls: unknown[];
  let updateCalls: { id: string; body: unknown }[];

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

  function create(): AdminCouponsPageComponent {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AdminCouponService,
          useValue: {
            listCoupons: () => listResult(),
            createCoupon: (body: unknown) => {
              createCalls.push(body);
              return of(coupon());
            },
            updateCoupon: (id: string, body: unknown) => {
              updateCalls.push({ id, body });
              return of(coupon());
            },
            deleteCoupon: () => of(undefined)
          }
        },
        { provide: ToastService, useValue: { success: () => 0, error: () => 0 } }
      ]
    });
    return TestBed.runInInjectionContext(() => new AdminCouponsPageComponent());
  }

  beforeEach(() => {
    listResult = () => of([]);
    createCalls = [];
    updateCalls = [];
  });

  it('loads coupons on construction', () => {
    listResult = () => of([coupon(), coupon({ id: 'c2' })]);
    const c = create();
    expect(c.loading()).toBe(false);
    expect(c.coupons().length).toBe(2);
  });

  it('openCreate resets the form to a valid default shape but invalid (no code/value)', () => {
    const c = create();
    c.openCreate();
    expect(c.formOpen()).toBe(true);
    expect(c.editingId()).toBeNull();
    expect(c.form.controls.type.value).toBe('PERCENTAGE');
    expect(c.form.controls.active.value).toBe(true);
    expect(c.form.invalid).toBe(true); // code + value still required
  });

  it('does not call the API when the form is invalid', () => {
    const c = create();
    c.openCreate();
    c.save();
    expect(createCalls).toEqual([]);
  });

  it('requires a value of at least 0.01', () => {
    const c = create();
    c.openCreate();
    c.form.controls.code.setValue('SAVE5');
    c.form.controls.value.setValue(0);
    expect(c.form.controls.value.valid).toBe(false);

    c.form.controls.value.setValue(0.01);
    expect(c.form.controls.value.valid).toBe(true);
  });

  it('rejects maxUses/maxUsesPerUser below 1 and a negative minimum order', () => {
    const c = create();
    c.openCreate();
    c.form.controls.maxUses.setValue(0);
    c.form.controls.maxUsesPerUser.setValue(0);
    c.form.controls.minimumOrderAmount.setValue(-1);
    expect(c.form.controls.maxUses.valid).toBe(false);
    expect(c.form.controls.maxUsesPerUser.valid).toBe(false);
    expect(c.form.controls.minimumOrderAmount.valid).toBe(false);

    c.form.controls.maxUses.setValue(1);
    c.form.controls.maxUsesPerUser.setValue(1);
    c.form.controls.minimumOrderAmount.setValue(0);
    expect(c.form.controls.maxUses.valid).toBe(true);
    expect(c.form.controls.maxUsesPerUser.valid).toBe(true);
    expect(c.form.controls.minimumOrderAmount.valid).toBe(true);
  });

  it('flags an end date before the start date at the group level', () => {
    const c = create();
    c.openCreate();
    c.form.controls.startsAt.setValue('2026-06-10T10:00');
    c.form.controls.endsAt.setValue('2026-06-01T10:00');
    expect(c.form.hasError('dateRange')).toBe(true);

    c.form.controls.endsAt.setValue('2026-06-20T10:00');
    expect(c.form.hasError('dateRange')).toBe(false);
  });

  it('a valid create sends a normalized request and closes the form', () => {
    const c = create();
    c.openCreate();
    c.form.controls.code.setValue('  save5  ');
    c.form.controls.type.setValue('FIXED_AMOUNT');
    c.form.controls.value.setValue(5);
    c.save();

    expect(createCalls.length).toBe(1);
    const body = createCalls[0] as Record<string, unknown>;
    expect(body['code']).toBe('save5'); // trimmed (server uppercases)
    expect(body['type']).toBe('FIXED_AMOUNT');
    expect(body['value']).toBe(5);
    expect(body['startsAt']).toBeNull();
    expect(body['endsAt']).toBeNull();
    expect(c.formOpen()).toBe(false);
  });

  it('openEdit prefills the form and a save calls updateCoupon with the id', () => {
    const c = create();
    c.openEdit(coupon({ id: 'c9', code: 'EDIT9', value: 15 }));
    expect(c.editingId()).toBe('c9');
    expect(c.form.controls.code.value).toBe('EDIT9');
    expect(c.form.controls.value.value).toBe(15);

    c.save();
    expect(updateCalls.length).toBe(1);
    expect(updateCalls[0].id).toBe('c9');
  });

  it('reactivate updates the coupon with active:true', () => {
    const c = create();
    c.reactivate(coupon({ id: 'c3', active: false }));
    expect(updateCalls.length).toBe(1);
    expect(updateCalls[0].id).toBe('c3');
    expect((updateCalls[0].body as Record<string, unknown>)['active']).toBe(true);
  });

  it('formatValue renders a percent or a currency amount by type', () => {
    const c = create();
    expect(c.formatValue(coupon({ type: 'PERCENTAGE', value: 10 }))).toBe('10%');
    expect(c.formatValue(coupon({ type: 'FIXED_AMOUNT', value: 5 }))).toContain('5');
  });
});
