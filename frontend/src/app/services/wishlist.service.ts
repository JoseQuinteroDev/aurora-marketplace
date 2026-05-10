import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { map, tap } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { WishlistItem } from '../core/models/wishlist.model';

@Injectable({ providedIn: 'root' })
export class WishlistService {
  private readonly wishlistSignal = signal<WishlistItem[]>([]);

  readonly wishlist = this.wishlistSignal.asReadonly();
  readonly count = computed(() => this.wishlistSignal().length);

  constructor(private readonly http: HttpClient) {}

  loadWishlist() {
    return this.http.get<ApiResponse<WishlistItem[]>>('/api/wishlist').pipe(
      map((response) => response.data ?? []),
      tap((items) => this.wishlistSignal.set(items))
    );
  }

  add(productId: string) {
    return this.http.post<ApiResponse<WishlistItem>>(`/api/wishlist/${productId}`, null).pipe(
      map((response) => response.data),
      tap((item) => {
        const exists = this.wishlistSignal().some((current) => current.productId === productId);
        if (!exists) {
          this.wishlistSignal.set([item, ...this.wishlistSignal()]);
        }
      })
    );
  }

  remove(productId: string) {
    return this.http.delete<ApiResponse<void>>(`/api/wishlist/${productId}`).pipe(
      tap(() => this.wishlistSignal.set(this.wishlistSignal().filter((item) => item.productId !== productId)))
    );
  }

  isWishlisted(productId: string): boolean {
    return this.wishlistSignal().some((item) => item.productId === productId);
  }
}
