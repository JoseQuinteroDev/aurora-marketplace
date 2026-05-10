import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { Order } from '../core/models/order.model';

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  constructor(private readonly http: HttpClient) {}

  confirm() {
    return this.http.post<ApiResponse<Order>>('/api/checkout/confirm', null).pipe(map((response) => response.data));
  }
}
