import { CurrencyPipe, NgClass } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule, CheckCircle2, CreditCard, ShieldCheck, XCircle } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
import { Order } from '../../core/models/order.model';
import { Payment } from '../../core/models/payment.model';
import { OrderService } from '../../services/order.service';
import { PaymentService } from '../../services/payment.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-payment-page',
  imports: [CurrencyPipe, NgClass, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell py-10 sm:py-12">
      <div class="premium-shell p-6 sm:p-8">
        <p class="section-kicker">{{ 'payment.title' | t }}</p>
        <h1 class="mt-3 text-4xl font-semibold leading-tight text-aurora-ink sm:text-5xl dark:text-white">{{ 'payment.title' | t }}</h1>
        <p class="mt-4 max-w-2xl text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'payment.subtitle' | t }}</p>
      </div>

      @if (loading()) {
        <div class="mt-8 grid gap-6 lg:grid-cols-[1fr_380px]">
          <div class="skeleton-line h-80 rounded-ui"></div>
          <div class="skeleton-line h-80 rounded-ui"></div>
        </div>
      } @else if (error()) {
        <div class="mt-8">
          <app-state-panel mode="error" title="{{ 'payment.error' | t }}" [message]="error()!" />
        </div>
      } @else if (order(); as data) {
        <div class="mt-8 grid gap-6 lg:grid-cols-[1fr_380px]">
          <div class="surface-panel p-6">
            <div class="flex items-center gap-3">
              <span class="flex h-12 w-12 items-center justify-center rounded-ui bg-aurora-pine/10 text-aurora-gold dark:bg-aurora-pine/10 dark:text-aurora-pinebright">
                <lucide-icon [img]="CreditCard" size="22" />
              </span>
              <div>
                <p class="text-sm font-bold text-aurora-muted dark:text-stone-400">{{ 'orders.number' | t }}</p>
                <h2 class="text-2xl font-extrabold text-aurora-ink dark:text-white">{{ data.orderNumber }}</h2>
              </div>
            </div>

            <div class="mt-6 rounded-ui border border-aurora-line bg-white p-5 dark:border-white/10 dark:bg-white/5">
              <div class="flex items-center justify-between gap-4">
                <span class="font-bold text-aurora-muted dark:text-stone-300">{{ 'cart.total' | t }}</span>
                <span class="text-3xl font-extrabold text-aurora-ink dark:text-white">{{ data.total | currency }}</span>
              </div>
            </div>

            @if (payment(); as result) {
              <div class="mt-6 rounded-ui border p-5" [ngClass]="result.paymentStatus === 'PAID' ? 'border-aurora-pine/30 bg-aurora-pine/10 dark:border-aurora-pine/40 dark:bg-aurora-pine/10' : 'border-aurora-rose/30 bg-aurora-rose/10 dark:border-aurora-rose/40 dark:bg-aurora-rose/10'">
                <div class="flex items-center gap-3">
                  @if (result.paymentStatus === 'PAID') {
                    <lucide-icon class="text-aurora-pine" [img]="CheckCircle2" size="24" />
                    <p class="font-extrabold text-aurora-pine dark:text-aurora-pinebright">{{ 'payment.paid' | t }}</p>
                  } @else {
                    <lucide-icon class="text-aurora-rose" [img]="XCircle" size="24" />
                    <p class="font-extrabold text-aurora-rose">{{ 'payment.failed' | t }}</p>
                  }
                </div>
                <p class="mt-2 text-sm text-aurora-muted dark:text-stone-300">{{ result.attempts[0]?.message }}</p>
              </div>
            }

            @if (payment()?.paymentStatus === 'PAID') {
              <a [routerLink]="['/account/orders', data.id]" class="ui-button ui-button-primary mt-6 w-full">
                <lucide-icon [img]="CheckCircle2" size="18" />
                {{ 'orders.detail' | t }}
              </a>
            } @else {
              <div class="mt-6 grid gap-3 sm:grid-cols-2">
                <button class="ui-button ui-button-accent w-full" type="button" [disabled]="processing()" (click)="simulate(true)">
                  <lucide-icon [img]="CheckCircle2" size="18" />
                  {{ 'payment.success' | t }}
                </button>
                <button class="ui-button ui-button-secondary w-full text-aurora-rose" type="button" [disabled]="processing()" (click)="simulate(false)">
                  <lucide-icon [img]="XCircle" size="18" />
                  {{ 'payment.failure' | t }}
                </button>
              </div>
            }
          </div>

          <aside class="surface-panel p-5 lg:sticky lg:top-28 lg:h-fit">
            <div class="flex items-center gap-2">
              <lucide-icon class="text-aurora-gold" [img]="ShieldCheck" size="20" />
              <h2 class="text-xl font-extrabold text-aurora-ink dark:text-white">{{ 'checkout.review' | t }}</h2>
            </div>
            <div class="mt-5 grid gap-3">
              @for (item of data.items; track item.id) {
                <div class="rounded-ui bg-stone-50 p-3 dark:bg-white/5">
                  <p class="font-extrabold text-aurora-ink dark:text-white">{{ item.productName }}</p>
                  <p class="mt-1 text-xs text-aurora-muted dark:text-stone-400">{{ item.quantity }} x {{ item.unitPrice | currency }}</p>
                </div>
              }
            </div>
            <a [routerLink]="['/account/orders', data.id]" class="ui-button ui-button-primary mt-5 w-full">{{ 'orders.detail' | t }}</a>
          </aside>
        </div>
      }
    </section>
  `
})
export class PaymentPageComponent implements OnInit {
  readonly order = signal<Order | null>(null);
  readonly payment = signal<Payment | null>(null);
  readonly loading = signal(true);
  readonly processing = signal(false);
  readonly error = signal<string | null>(null);

  readonly CheckCircle2 = CheckCircle2;
  readonly CreditCard = CreditCard;
  readonly ShieldCheck = ShieldCheck;
  readonly XCircle = XCircle;

  private orderId = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly language: LanguageService,
    private readonly orderService: OrderService,
    private readonly paymentService: PaymentService
  ) {}

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('id') ?? '';
    if (!this.orderId) {
      this.error.set(this.language.translate('orders.error'));
      this.loading.set(false);
      return;
    }

    this.orderService.getOrder(this.orderId).subscribe({
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

  simulate(success: boolean): void {
    this.processing.set(true);
    this.error.set(null);
    this.paymentService.simulate(this.orderId, {
      success,
      message: success ? this.language.translate('payment.approvedMessage') : this.language.translate('payment.declinedMessage')
    }).subscribe({
      next: (payment) => {
        this.payment.set(payment);
        this.processing.set(false);
      },
      error: () => {
        this.error.set(this.language.translate('payment.error'));
        this.processing.set(false);
      }
    });
  }
}
