import { OrderStatus } from './order.model';

export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';
export type PaymentMethod = 'SIMULATED_CARD';

export interface PaymentAttempt {
  id: string;
  success: boolean;
  status: PaymentStatus;
  message: string | null;
  createdAt: string;
}

export interface Payment {
  id: string;
  orderId: string;
  orderNumber: string;
  orderStatus: OrderStatus;
  paymentStatus: PaymentStatus;
  method: PaymentMethod;
  amount: number;
  attempts: PaymentAttempt[];
  createdAt: string;
  updatedAt: string;
}

export interface PaymentSimulationRequest {
  success: boolean;
  message: string | null;
}
