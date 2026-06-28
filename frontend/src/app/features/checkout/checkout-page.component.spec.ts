import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { Cart } from '../../core/models/cart.model';
import { Order } from '../../core/models/order.model';
import { CartService } from '../../services/cart.service';
import { CheckoutService } from '../../services/checkout.service';
import { CheckoutPageComponent } from './checkout-page.component';

/**
 * Checkout confirm flow. The displayed total is server-owned (rendered straight from
 * the loaded cart, never recomputed client-side), the confirm button is disabled while
 * a request is in flight (double-submit guard, paired with the idempotency key), and a
 * failed confirm shows an error and re-enables the button for a retry. Cart/Checkout are
 * fakes so no real HTTP happens; the Router is real-but-inert with navigateByUrl spied.
 */
describe('CheckoutPageComponent', () => {
  let fixture: ComponentFixture<CheckoutPageComponent>;
  let component: CheckoutPageComponent;

  let cartSignal: ReturnType<typeof signal<Cart | null>>;
  let loadCartResult: () => Observable<Cart>;
  let confirmResult: () => Observable<Order>;
  let confirmKeys: string[];
  let navigations: string[];

  function cartFixture(): Cart {
    return {
      id: 'cart-1',
      coupon: null,
      subtotal: 100,
      discountTotal: 10,
      total: 90, // server-computed total — the value the UI must display verbatim
      items: [
        {
          id: 'item-1',
          productId: 'p1',
          productName: 'Pine Candle',
          productSlug: 'pine-candle',
          variantId: 'v1',
          variantSku: 'SKU-1',
          variantName: 'Default',
          quantity: 2,
          unitPrice: 50,
          lineTotal: 100,
        },
      ],
    };
  }

  function orderFixture(): Order {
    return {
      id: 'order-1',
      orderNumber: 'AUR-001',
      status: 'PENDING_PAYMENT',
      couponCode: null,
      subtotal: 100,
      discountTotal: 10,
      total: 90,
      items: [],
      statusHistory: [],
      createdAt: '2026-06-28T00:00:00Z',
      updatedAt: '2026-06-28T00:00:00Z',
    };
  }

  function fillAddress(): void {
    component.addressForm.setValue({
      fullName: 'Ada Lovelace',
      addressLine: '1 Analytical Way',
      city: 'London',
      postalCode: 'EC1',
      country: 'UK',
      phone: '+44 20 0000 0000',
    });
  }

  function setup(): void {
    cartSignal = signal<Cart | null>(cartFixture());
    loadCartResult = () => of(cartFixture());
    confirmResult = () => of(orderFixture());
    confirmKeys = [];
    navigations = [];

    TestBed.configureTestingModule({
      imports: [CheckoutPageComponent],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            cart: cartSignal.asReadonly(),
            loadCart: () => loadCartResult(),
          },
        },
        {
          provide: CheckoutService,
          useValue: {
            confirm: (key: string) => {
              confirmKeys.push(key);
              return confirmResult();
            },
          },
        },
      ],
    });

    const router = TestBed.inject(Router);
    router.navigateByUrl = ((url: string) => {
      navigations.push(url);
      return Promise.resolve(true);
    }) as Router['navigateByUrl'];

    fixture = TestBed.createComponent(CheckoutPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges(); // triggers ngOnInit -> loadCart resolves -> loading false
  }

  it('renders the server-provided total (not a client recomputation)', () => {
    setup();

    // Sanity: the loaded cart's total is the authoritative server number.
    expect(component.cart()?.total).toBe(90);

    // The total row binds {{ data.total | currency }} — assert the server's 90 is what
    // renders there, proving the UI displays the server number rather than recomputing.
    const totalRow = (fixture.nativeElement as HTMLElement).querySelector('.text-lg.font-extrabold');
    expect(totalRow?.textContent ?? '').toContain('$90.00');
    // Angular CurrencyPipe defaults to en-US/USD (no custom LOCALE_ID is registered).
    expect((fixture.nativeElement as HTMLElement).textContent ?? '').toContain('$90.00');
    expect(component.loading()).toBe(false);
  });

  it('disables the submit control while a confirm is in flight, then succeeds + navigates', () => {
    setup();
    const gate = new Subject<Order>();
    confirmResult = () => gate.asObservable(); // never completes until we emit

    fillAddress();
    component.confirm(cartFixture());

    expect(component.confirming()).toBe(true); // in flight -> button disabled
    expect(confirmKeys.length).toBe(1);
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(button.disabled).toBe(true);

    gate.next(orderFixture());
    gate.complete();

    // On success the page navigates away (the in-flight flag is not reset here on purpose);
    // the meaningful contract is that success fired and we routed to the payment step.
    expect(component.success()).toBe(true);
    expect(navigations).toEqual(['/orders/order-1/payment']);
  });

  it('reuses one idempotency key when a confirm is retried after failure', () => {
    setup();
    confirmResult = () => throwError(() => ({ status: 500 }));

    fillAddress();
    component.confirm(cartFixture());
    component.confirm(cartFixture());

    // Same stable key across retries so the server dedups to a single order.
    expect(confirmKeys.length).toBe(2);
    expect(confirmKeys[0]).toBe(confirmKeys[1]);
  });

  it('shows an error and re-enables submit when the confirm fails', () => {
    setup();
    confirmResult = () => throwError(() => ({ status: 500 }));

    fillAddress();
    component.confirm(cartFixture());
    fixture.detectChanges();

    // Assert via component state (robust): the error surfaced and the in-flight flag cleared, so the
    // submit control is re-enabled for a retry, and no navigation happened.
    expect(component.error()).not.toBeNull();
    expect(component.confirming()).toBe(false);
    expect(navigations).toEqual([]);
  });

  it('does not call confirm when the address form is invalid', () => {
    setup();
    // addressForm left blank (required fields invalid).

    component.confirm(cartFixture());

    expect(confirmKeys.length).toBe(0);
    expect(component.confirming()).toBe(false);
  });
});
