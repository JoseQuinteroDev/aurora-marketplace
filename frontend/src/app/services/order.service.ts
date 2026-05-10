import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { Order } from '../core/models/order.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  constructor(private readonly http: HttpClient) {}

  listOrders() {
    return this.http.get<ApiResponse<Order[]>>('/api/orders').pipe(map((response) => response.data ?? []));
  }

  getOrder(id: string) {
    return this.http.get<ApiResponse<Order>>(`/api/orders/${id}`).pipe(map((response) => response.data));
  }
}
