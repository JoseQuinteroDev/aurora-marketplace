import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { LucideAngularModule, ArrowUpRight, ReceiptText } from 'lucide-angular';
import { LanguageService } from '../../../core/i18n/language.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { AdminOrder, OrderStatus } from '../../../core/models/admin-order.model';
import { AdminOrderService } from '../../../services/admin-order.service';
import { StatePanelComponent } from '../../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-admin-orders-page',
  imports: [CurrencyPipe, DatePipe, NgClass, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="px-4 py-8 sm:px-6 lg:px-8">
      <div class="max-w-7xl">
        <p class="text-xs font-bold uppercase tracking-[0.18em] text-aurora-pinebright">{{ 'admin.orders.eyebrow' | t }}</p>
        <h1 class="mt-3 text-3xl font-extrabold tracking-normal text-white sm:text-4xl">{{ 'admin.orders.title' | t }}</h1>
        <p class="mt-3 max-w-2xl text-sm leading-6 text-aurora-mist/70">{{ 'admin.orders.subtitle' | t }}</p>

        @if (loading()) {
          <div class="mt-8 grid gap-3">
            @for (item of [1, 2, 3, 4, 5]; track item) {
              <div class="h-16 animate-pulse rounded-ui bg-white/10"></div>
            }
          </div>
        } @else if (error()) {
          <div class="mt-8">
            <app-state-panel mode="error" title="{{ 'admin.orders.errorTitle' | t }}" [message]="error()!" />
            <button class="ui-button mt-6 border border-white/10 bg-white/10 text-white hover:bg-white/15" type="button" (click)="load()">
              {{ 'common.retry' | t }}
            </button>
          </div>
        } @else if (orders().length === 0) {
          <div class="mt-8">
            <app-state-panel title="{{ 'admin.orders.emptyTitle' | t }}" message="{{ 'admin.orders.empty' | t }}" />
          </div>
        } @else {
          <!-- Desktop: a real table. Mobile: stacked cards (see below). -->
          <div class="mt-8 hidden overflow-hidden rounded-ui border border-white/10 md:block">
            <table class="w-full text-left text-sm">
              <caption class="sr-only">{{ 'admin.orders.title' | t }}</caption>
              <thead class="bg-white/[0.06] text-xs uppercase tracking-[0.12em] text-aurora-mist/70">
                <tr>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.orders.col.order' | t }}</th>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.orders.col.status' | t }}</th>
                  <th scope="col" class="px-4 py-3 text-right font-semibold">{{ 'admin.orders.col.total' | t }}</th>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.orders.col.date' | t }}</th>
                  <th scope="col" class="px-4 py-3"><span class="sr-only">{{ 'common.view' | t }}</span></th>
                </tr>
              </thead>
              <tbody>
                @for (order of orders(); track order.id) {
                  <tr
                    class="cursor-pointer border-t border-white/10 transition-colors duration-150 hover:bg-white/[0.05] focus-within:bg-white/[0.05]"
                    (click)="open(order)"
                  >
                    <td class="px-4 py-3">
                      <button
                        class="cursor-pointer font-extrabold text-white outline-none focus-visible:underline"
                        type="button"
                        (click)="open(order); $event.stopPropagation()"
                        [attr.aria-label]="('admin.orders.openLabel' | t) + ' ' + order.orderNumber"
                      >
                        {{ order.orderNumber }}
                      </button>
                    </td>
                    <td class="px-4 py-3">
                      <span class="aurora-badge" [ngClass]="statusClass(order.status)">{{ ('order.status.' + order.status) | t }}</span>
                    </td>
                    <td class="px-4 py-3 text-right font-extrabold text-white">{{ order.total | currency }}</td>
                    <td class="px-4 py-3 text-aurora-mist/70">{{ order.createdAt | date: 'medium' }}</td>
                    <td class="px-4 py-3 text-right">
                      <lucide-icon class="inline text-aurora-pinebright" [img]="ArrowUpRight" size="18" />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="mt-8 grid gap-3 md:hidden">
            @for (order of orders(); track order.id) {
              <button
                class="cursor-pointer rounded-ui border border-white/10 bg-white/[0.06] p-4 text-left outline-none transition-colors duration-150 hover:bg-white/[0.09] focus-visible:ring-1 focus-visible:ring-aurora-pinebright"
                type="button"
                (click)="open(order)"
                [attr.aria-label]="('admin.orders.openLabel' | t) + ' ' + order.orderNumber"
              >
                <div class="flex items-center justify-between gap-3">
                  <span class="flex items-center gap-2 font-extrabold text-white">
                    <lucide-icon class="text-aurora-pinebright" [img]="ReceiptText" size="18" />
                    {{ order.orderNumber }}
                  </span>
                  <span class="aurora-badge" [ngClass]="statusClass(order.status)">{{ ('order.status.' + order.status) | t }}</span>
                </div>
                <div class="mt-3 flex items-center justify-between text-sm">
                  <span class="text-aurora-mist/70">{{ order.createdAt | date: 'medium' }}</span>
                  <span class="font-extrabold text-white">{{ order.total | currency }}</span>
                </div>
              </button>
            }
          </div>
        }
      </div>
    </section>
  `
})
export class AdminOrdersPageComponent {
  private readonly adminOrderService = inject(AdminOrderService);
  private readonly language = inject(LanguageService);
  private readonly router = inject(Router);

  readonly orders = signal<AdminOrder[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly ArrowUpRight = ArrowUpRight;
  readonly ReceiptText = ReceiptText;

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.adminOrderService.listOrders().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.language.translate('admin.orders.error'));
        this.loading.set(false);
      }
    });
  }

  open(order: AdminOrder): void {
    this.router.navigate(['/admin/orders', order.id]);
  }

  statusClass(status: OrderStatus): string {
    if (status === 'PAID' || status === 'DELIVERED') {
      return 'bg-aurora-pine/20 text-aurora-pinebright';
    }
    if (status === 'CANCELLED' || status === 'REFUNDED') {
      return 'bg-aurora-rose/20 text-aurora-rose';
    }
    return 'bg-white/10 text-aurora-mist';
  }
}
