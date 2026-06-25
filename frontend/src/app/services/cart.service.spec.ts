import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Cart } from '../core/models/cart.model';
import { AuthService } from './auth.service';
import { CartService } from './cart.service';

function cartWith(...quantities: number[]): Cart {
  return {
    id: 'c1',
    items: quantities.map((q, i) => ({
      id: `i${i}`,
      productId: 'p',
      productName: 'n',
      productSlug: 's',
      variantId: 'v',
      variantSku: 'sku',
      variantName: 'vn',
      quantity: q,
      unitPrice: 10,
      lineTotal: 10 * q,
    })),
    coupon: null,
    subtotal: 0,
    discountTotal: 0,
    total: 0,
  };
}

describe('CartService', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // No live session: the sync effect just clears the cart, no HTTP on boot.
        { provide: AuthService, useValue: { currentUser: () => null } },
      ],
    });
    return {
      service: TestBed.inject(CartService),
      http: TestBed.inject(HttpTestingController),
    };
  }

  it('itemCount is zero when no cart is loaded', () => {
    const { service } = setup();
    expect(service.itemCount()).toBe(0);
  });

  it('addItem posts and updates the cart signal + itemCount', () => {
    const { service, http } = setup();

    service.addItem({ variantId: 'v', quantity: 2 }).subscribe();
    http.expectOne('/api/cart/items').flush({ data: cartWith(2, 3) });

    expect(service.cart()?.id).toBe('c1');
    expect(service.itemCount()).toBe(5);   // 2 + 3
    http.verify();
  });

  it('removeItem deletes and refreshes the cart', () => {
    const { service, http } = setup();
    service.addItem({ variantId: 'v', quantity: 2 }).subscribe();
    http.expectOne('/api/cart/items').flush({ data: cartWith(2, 3) });

    service.removeItem('i0').subscribe();
    http.expectOne('/api/cart/items/i0').flush({ data: cartWith(3) });

    expect(service.itemCount()).toBe(3);
    http.verify();
  });

  it('loadCart fetches and stores the cart', () => {
    const { service, http } = setup();

    service.loadCart().subscribe();
    http.expectOne('/api/cart').flush({ data: cartWith(1) });

    expect(service.itemCount()).toBe(1);
    http.verify();
  });
});
