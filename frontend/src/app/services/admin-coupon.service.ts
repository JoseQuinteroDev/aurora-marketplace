import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { AdminCoupon, AdminCouponRequest } from '../core/models/admin-coupon.model';

@Injectable({ providedIn: 'root' })
export class AdminCouponService {
  private readonly http = inject(HttpClient);

  /** All coupons, including inactive (soft-deleted) ones. */
  listCoupons() {
    return this.http
      .get<ApiResponse<AdminCoupon[]>>('/api/admin/coupons')
      .pipe(map((response) => response.data ?? []));
  }

  createCoupon(request: AdminCouponRequest) {
    return this.http
      .post<ApiResponse<AdminCoupon>>('/api/admin/coupons', request)
      .pipe(map((response) => response.data));
  }

  updateCoupon(id: string, request: AdminCouponRequest) {
    return this.http
      .put<ApiResponse<AdminCoupon>>(`/api/admin/coupons/${id}`, request)
      .pipe(map((response) => response.data));
  }

  /** Soft-deletes (deactivates) a coupon. Reactivate by updating with `active: true`. */
  deleteCoupon(id: string) {
    return this.http
      .delete<ApiResponse<void>>(`/api/admin/coupons/${id}`)
      .pipe(map((response) => response.data));
  }
}
