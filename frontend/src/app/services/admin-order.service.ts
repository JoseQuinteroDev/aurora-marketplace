import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { AdminOrder, AdminOrderStatusUpdate } from '../core/models/admin-order.model';

@Injectable({ providedIn: 'root' })
export class AdminOrderService {
  private readonly http = inject(HttpClient);

  /** All orders, newest first (server-sorted). */
  listOrders() {
    return this.http
      .get<ApiResponse<AdminOrder[]>>('/api/admin/orders')
      .pipe(map((response) => response.data ?? []));
  }

  getOrder(id: string) {
    return this.http
      .get<ApiResponse<AdminOrder>>(`/api/admin/orders/${id}`)
      .pipe(map((response) => response.data));
  }

  /** Moves an order to a new status (gated server-side to legal transitions). */
  updateStatus(id: string, update: AdminOrderStatusUpdate) {
    return this.http
      .patch<ApiResponse<AdminOrder>>(`/api/admin/orders/${id}/status`, update)
      .pipe(map((response) => response.data));
  }
}
