import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { Order } from '../core/models/order.model';

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  constructor(private readonly http: HttpClient) {}

  /**
   * Confirms checkout. The caller passes a stable Idempotency-Key for the checkout intent
   * (reused across retries) so a double-click or a retried request resolves to a single
   * order server-side rather than creating duplicates.
   */
  confirm(idempotencyKey: string) {
    return this.http
      .post<ApiResponse<Order>>('/api/checkout/confirm', null, {
        headers: { 'Idempotency-Key': idempotencyKey }
      })
      .pipe(map((response) => response.data));
  }
}
