import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowUpRight, PackageCheck, ReceiptText } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
import { Order, OrderStatus } from '../../core/models/order.model';
import { OrderService } from '../../services/order.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-orders-page',
  imports: [CurrencyPipe, DatePipe, NgClass, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell py-10 sm:py-12">
      <div class="premium-shell p-6 sm:p-8">
        <p class="section-kicker">{{ 'nav.orders' | t }}</p>
        <h1 class="mt-3 text-4xl font-black leading-tight text-aurora-ink sm:text-5xl dark:text-white">{{ 'orders.title' | t }}</h1>
        <p class="mt-4 max-w-2xl text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'orders.subtitle' | t }}</p>
      </div>

      @if (loading()) {
        <div class="mt-8 grid gap-4">
          @for (item of [1, 2, 3]; track item) {
            <div class="skeleton-line h-32 rounded-ui"></div>
          }
        </div>
      } @else if (error()) {
        <div class="mt-8">
          <app-state-panel mode="error" title="{{ 'orders.error' | t }}" [message]="error()!" />
        </div>
      } @else if (orders().length === 0) {
        <div class="mt-8">
          <app-state-panel title="{{ 'orders.empty' | t }}" message="{{ 'orders.emptyMessage' | t }}" />
          <div class="mt-6 text-center">
            <a routerLink="/catalog" class="ui-button ui-button-primary">{{ 'cart.keepShopping' | t }}</a>
          </div>
        </div>
      } @else {
        <div class="mt-8 grid gap-4">
          @for (order of orders(); track order.id) {
            <article class="surface-panel p-5">
              <div class="grid gap-4 md:grid-cols-[1fr_auto] md:items-center">
                <div class="flex items-start gap-4">
                  <span class="flex h-12 w-12 shrink-0 items-center justify-center rounded-ui bg-amber-50 text-aurora-gold dark:bg-amber-400/10 dark:text-amber-300">
                    <lucide-icon [img]="ReceiptText" size="22" />
                  </span>
                  <div>
                    <div class="flex flex-wrap items-center gap-3">
                      <h2 class="text-xl font-black text-aurora-ink dark:text-white">{{ order.orderNumber }}</h2>
                      <span class="aurora-badge" [ngClass]="statusClass(order.status)">{{ order.status }}</span>
                    </div>
                    <p class="mt-2 text-sm text-aurora-muted dark:text-stone-300">{{ order.createdAt | date:'medium' }}</p>
                    <p class="mt-3 text-sm font-semibold text-aurora-muted dark:text-stone-300">{{ order.items.length }} {{ 'orders.items' | t }}</p>
                  </div>
                </div>
                <div class="flex flex-wrap items-center gap-3 md:justify-end">
                  <p class="text-2xl font-black text-aurora-ink dark:text-white">{{ order.total | currency }}</p>
                  @if (order.status === 'PENDING_PAYMENT' || order.status === 'CREATED') {
                    <a [routerLink]="['/orders', order.id, 'payment']" class="ui-button ui-button-accent">{{ 'orders.pay' | t }}</a>
                  }
                  <a [routerLink]="['/account/orders', order.id]" class="ui-button ui-button-primary">
                    {{ 'common.view' | t }}
                    <lucide-icon [img]="ArrowUpRight" size="17" />
                  </a>
                </div>
              </div>
            </article>
          }
        </div>
      }
    </section>
  `
})
export class OrdersPageComponent implements OnInit {
  readonly orders = signal<Order[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly ArrowUpRight = ArrowUpRight;
  readonly PackageCheck = PackageCheck;
  readonly ReceiptText = ReceiptText;

  constructor(
    private readonly language: LanguageService,
    private readonly orderService: OrderService
  ) {}

  ngOnInit(): void {
    this.orderService.listOrders().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.language.translate('orders.error'));
        this.loading.set(false);
      }
    });
  }

  statusClass(status: OrderStatus): string {
    if (status === 'PAID' || status === 'DELIVERED') {
      return 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300';
    }

    if (status === 'CANCELLED' || status === 'REFUNDED') {
      return 'bg-rose-50 text-aurora-rose dark:bg-rose-500/15';
    }

    return 'bg-amber-50 text-aurora-gold dark:bg-amber-400/10 dark:text-amber-300';
  }
}
