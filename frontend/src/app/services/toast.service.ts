import { Injectable, signal } from '@angular/core';

export type ToastTone = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
}

/**
 * App-wide, signal-backed toast queue. Components pass an already-translated
 * message; the ToastHostComponent renders the queue in an aria-live region.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private sequence = 0;
  private readonly toastsSignal = signal<Toast[]>([]);

  readonly toasts = this.toastsSignal.asReadonly();

  show(message: string, tone: ToastTone = 'info', durationMs = 4000): number {
    const id = ++this.sequence;
    this.toastsSignal.update((toasts) => [...toasts, { id, tone, message }]);

    if (durationMs > 0) {
      setTimeout(() => this.dismiss(id), durationMs);
    }

    return id;
  }

  success(message: string, durationMs?: number): number {
    return this.show(message, 'success', durationMs);
  }

  error(message: string, durationMs?: number): number {
    return this.show(message, 'error', durationMs);
  }

  info(message: string, durationMs?: number): number {
    return this.show(message, 'info', durationMs);
  }

  dismiss(id: number): void {
    this.toastsSignal.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }
}
