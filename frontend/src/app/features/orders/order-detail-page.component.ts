import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowLeft, CreditCard, PackageCheck, ReceiptText } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
import { Order, OrderStatus } from '../../core/models/order.model';
import { OrderService } from '../../services/order.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-order-detail-page',
  imports: [CurrencyPipe, DatePipe, NgClass, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell py-10 sm:py-12">
      <a routerLink="/account/orders" class="premium-link inline-flex items-center gap-2 text-sm">
        <lucide-icon [img]="ArrowLeft" size="17" />
        {{ 'nav.orders' | t }}
      </a>

      @if (loading()) {
        <div class="mt-8 grid gap-6 lg:grid-cols-[1fr_380px]">
          <div class="skeleton-line h-96 rounded-ui"></div>
          <div class="skeleton-line h-80 rounded-ui"></div>
        </div>
      } @else if (error()) {
        <div class="mt-8">
          <app-state-panel mode="error" title="{{ 'orders.error' | t }}" [message]="error()!" />
        </div>
      } @else if (order(); as data) {
        <div class="mt-8 grid gap-6 lg:grid-cols-[1fr_380px]">
          <div class="space-y-6">
            <div class="premium-shell p-6 sm:p-8">
              <p class="section-kicker">{{ 'orders.detail' | t }}</p>
              <div class="mt-3 flex flex-wrap items-center gap-3">
                <h1 class="text-4xl font-semibold leading-tight text-aurora-ink sm:text-5xl dark:text-white">{{ data.orderNumber }}</h1>
                <span class="aurora-badge" [ngClass]="statusClass(data.status)">{{ ('order.status.' + data.status) | t }}</span>
              </div>
              <p class="mt-3 text-sm text-aurora-muted dark:text-stone-300">{{ data.createdAt | date:'medium' }}</p>
            </div>

            <div class="surface-panel p-5">
              <div class="flex items-center gap-2">
                <lucide-icon class="text-aurora-gold" [img]="PackageCheck" size="20" />
                <h2 class="text-xl font-black text-aurora-ink dark:text-white">{{ 'orders.items' | t }}</h2>
              </div>
              <div class="mt-5 grid gap-3">
                @for (item of data.items; track item.id) {
                  <div class="grid gap-3 rounded-ui bg-stone-50 p-4 dark:bg-white/5 sm:grid-cols-[1fr_auto] sm:items-center">
                    <div>
                      <a [routerLink]="['/products', item.productSlug]" class="cursor-pointer font-black text-aurora-ink transition-colors duration-200 hover:text-aurora-gold dark:text-white">{{ item.productName }}</a>
                      <p class="mt-1 text-sm text-aurora-muted dark:text-stone-400">{{ item.variantName }} / {{ item.variantSku }}</p>
                      <p class="mt-2 text-sm font-semibold text-aurora-muted dark:text-stone-300">{{ item.quantity }} x {{ item.unitPrice | currency }}</p>
                    </div>
                    <p class="text-xl font-black text-aurora-ink dark:text-white">{{ item.lineTotal | currency }}</p>
                  </div>
                }
              </div>
            </div>

            <div class="surface-panel p-5">
              <div class="flex items-center gap-2">
                <lucide-icon class="text-aurora-gold" [img]="ReceiptText" size="20" />
                <h2 class="text-xl font-black text-aurora-ink dark:text-white">{{ 'orders.timeline' | t }}</h2>
              </div>
              <div class="mt-5 grid gap-3">
                @for (history of data.statusHistory; track history.id) {
                  <div class="flex gap-3">
                    <span class="mt-1 h-3 w-3 shrink-0 rounded-full bg-aurora-amber"></span>
                    <div>
                      <p class="font-black text-aurora-ink dark:text-white">{{ ('order.status.' + history.status) | t }}</p>
                      <p class="text-sm text-aurora-muted dark:text-stone-400">{{ history.createdAt | date:'medium' }}</p>
                      @if (history.note) {
                        <p class="mt-1 text-sm text-aurora-muted dark:text-stone-300">{{ history.note }}</p>
                      }
                    </div>
                  </div>
                } @empty {
                  <p class="text-sm text-aurora-muted dark:text-stone-300">{{ ('order.status.' + data.status) | t }}</p>
                }
              </div>
            </div>
          </div>

          <aside class="surface-panel p-5 lg:sticky lg:top-28 lg:h-fit">
            <div class="flex items-center gap-2">
              <lucide-icon class="text-aurora-gold" [img]="CreditCard" size="20" />
              <h2 class="text-xl font-black text-aurora-ink dark:text-white">{{ 'cart.summary' | t }}</h2>
            </div>
            <div class="mt-5 space-y-3 text-sm">
              <div class="flex justify-between text-aurora-muted dark:text-stone-300">
                <span>{{ 'cart.subtotal' | t }}</span>
                <span class="font-bold">{{ data.subtotal | currency }}</span>
              </div>
              <div class="flex justify-between text-aurora-muted dark:text-stone-300">
                <span>{{ 'cart.discount' | t }}</span>
                <span class="font-bold text-aurora-emerald">-{{ data.discountTotal | currency }}</span>
              </div>
              @if (data.couponCode) {
                <div class="flex justify-between text-aurora-muted dark:text-stone-300">
                  <span>{{ 'cart.coupon' | t }}</span>
                  <span class="font-bold">{{ data.couponCode }}</span>
                </div>
              }
              <div class="border-t border-aurora-line pt-3 text-lg font-black text-aurora-ink dark:border-white/10 dark:text-white">
                <div class="flex justify-between">
                  <span>{{ 'cart.total' | t }}</span>
                  <span>{{ data.total | currency }}</span>
                </div>
              </div>
            </div>
            @if (data.status === 'PENDING_PAYMENT' || data.status === 'CREATED') {
              <a [routerLink]="['/orders', data.id, 'payment']" class="ui-button ui-button-accent mt-5 w-full">{{ 'orders.pay' | t }}</a>
            }
          </aside>
        </div>
      }
    </section>
  `
})
export class OrderDetailPageComponent implements OnInit {
  readonly order = signal<Order | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly ArrowLeft = ArrowLeft;
  readonly CreditCard = CreditCard;
  readonly PackageCheck = PackageCheck;
  readonly ReceiptText = ReceiptText;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly language: LanguageService,
    private readonly orderService: OrderService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set(this.language.translate('orders.error'));
      this.loading.set(false);
      return;
    }

    this.orderService.getOrder(id).subscribe({
      next: (order) => {
        this.order.set(order);
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
      return 'bg-aurora-pine/10 text-aurora-pine dark:bg-aurora-pine/15 dark:text-aurora-pinebright';
    }

    if (status === 'CANCELLED' || status === 'REFUNDED') {
      return 'bg-aurora-rose/10 text-aurora-rose dark:bg-aurora-rose/15';
    }

    return 'bg-aurora-pine/10 text-aurora-gold dark:bg-aurora-pine/10 dark:text-aurora-pinebright';
  }
}
