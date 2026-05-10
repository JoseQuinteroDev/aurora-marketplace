import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { Payment, PaymentSimulationRequest } from '../core/models/payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  constructor(private readonly http: HttpClient) {}

  simulate(orderId: string, request: PaymentSimulationRequest) {
    return this.http
      .post<ApiResponse<Payment>>(`/api/payments/${orderId}/simulate`, request)
      .pipe(map((response) => response.data));
  }
}
