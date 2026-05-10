import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { map, tap } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { AddCartItemRequest, ApplyCouponRequest, Cart, UpdateCartItemRequest } from '../core/models/cart.model';

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly cartSignal = signal<Cart | null>(null);

  readonly cart = this.cartSignal.asReadonly();
  readonly itemCount = computed(() => this.cartSignal()?.items.reduce((total, item) => total + item.quantity, 0) ?? 0);

  constructor(private readonly http: HttpClient) {}

  loadCart() {
    return this.http.get<ApiResponse<Cart>>('/api/cart').pipe(
      map((response) => response.data),
      tap((cart) => this.cartSignal.set(cart))
    );
  }

  addItem(request: AddCartItemRequest) {
    return this.http.post<ApiResponse<Cart>>('/api/cart/items', request).pipe(
      map((response) => response.data),
      tap((cart) => this.cartSignal.set(cart))
    );
  }

  updateItem(itemId: string, request: UpdateCartItemRequest) {
    return this.http.patch<ApiResponse<Cart>>(`/api/cart/items/${itemId}`, request).pipe(
      map((response) => response.data),
      tap((cart) => this.cartSignal.set(cart))
    );
  }

  removeItem(itemId: string) {
    return this.http.delete<ApiResponse<Cart>>(`/api/cart/items/${itemId}`).pipe(
      map((response) => response.data),
      tap((cart) => this.cartSignal.set(cart))
    );
  }

  clearCart() {
    return this.http.delete<ApiResponse<Cart>>('/api/cart').pipe(
      map((response) => response.data),
      tap((cart) => this.cartSignal.set(cart))
    );
  }

  applyCoupon(request: ApplyCouponRequest) {
    return this.http.post<ApiResponse<Cart>>('/api/cart/apply-coupon', request).pipe(
      map((response) => response.data),
      tap((cart) => this.cartSignal.set(cart))
    );
  }

  removeCoupon() {
    return this.http.delete<ApiResponse<Cart>>('/api/cart/coupon').pipe(
      map((response) => response.data),
      tap((cart) => this.cartSignal.set(cart))
    );
  }
}
