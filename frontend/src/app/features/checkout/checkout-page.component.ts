import { CurrencyPipe, NgClass } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule, CheckCircle2, CreditCard, MapPin, PackageCheck, ShieldCheck } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
import { Cart } from '../../core/models/cart.model';
import { CartService } from '../../services/cart.service';
import { CheckoutService } from '../../services/checkout.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-checkout-page',
  imports: [CurrencyPipe, NgClass, ReactiveFormsModule, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell py-10 sm:py-12">
      <div class="premium-shell p-6 sm:p-8">
        <p class="section-kicker">{{ 'checkout.title' | t }}</p>
        <h1 class="mt-3 text-4xl font-semibold leading-tight text-aurora-ink sm:text-5xl dark:text-white">{{ 'checkout.title' | t }}</h1>
        <p class="mt-4 max-w-2xl text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'checkout.subtitle' | t }}</p>

        <div class="mt-8 grid gap-3 sm:grid-cols-3">
          @for (step of steps; track step.key; let index = $index) {
            <div class="rounded-ui border border-aurora-line bg-white/80 p-4 dark:border-white/10 dark:bg-white/5">
              <div class="flex items-center gap-3">
                <span class="flex h-9 w-9 items-center justify-center rounded-ui bg-aurora-ink text-sm font-black text-white dark:bg-white dark:text-aurora-night">{{ index + 1 }}</span>
                <div>
                  <p class="text-sm font-black text-aurora-ink dark:text-white">{{ step.label | t }}</p>
                  <p class="mt-1 text-xs text-aurora-muted dark:text-stone-400">{{ step.copy | t }}</p>
                </div>
              </div>
            </div>
          }
        </div>
      </div>

      @if (loading()) {
        <div class="mt-8 grid gap-6 lg:grid-cols-[1fr_380px]">
          <div class="skeleton-line h-96 rounded-ui"></div>
          <div class="skeleton-line h-80 rounded-ui"></div>
        </div>
      } @else if (error()) {
        <div class="mt-8">
          <app-state-panel mode="error" title="{{ 'checkout.error' | t }}" [message]="error()!" />
        </div>
      } @else if (cart(); as data) {
        @if (data.items.length === 0) {
          <div class="mt-8">
            <app-state-panel title="{{ 'cart.empty' | t }}" message="{{ 'cart.emptyMessage' | t }}" />
            <div class="mt-6 text-center">
              <a routerLink="/catalog" class="ui-button ui-button-primary">{{ 'cart.keepShopping' | t }}</a>
            </div>
          </div>
        } @else {
          <div class="mt-8 grid gap-6 lg:grid-cols-[1fr_380px]">
            <form class="surface-panel p-5 sm:p-6" [formGroup]="addressForm" (ngSubmit)="confirm(data)">
              <div class="flex items-center gap-3">
                <span class="flex h-11 w-11 items-center justify-center rounded-ui bg-aurora-pine/10 text-aurora-gold dark:bg-aurora-pine/10 dark:text-aurora-pinebright">
                  <lucide-icon [img]="MapPin" size="21" />
                </span>
                <div>
                  <h2 class="text-xl font-black text-aurora-ink dark:text-white">{{ 'checkout.address' | t }}</h2>
                  <p class="text-sm text-aurora-muted dark:text-stone-300">{{ 'checkout.uiOnly' | t }}</p>
                </div>
              </div>

              <div class="mt-6 grid gap-4 sm:grid-cols-2">
                @for (field of addressFields; track field.name) {
                  <label class="block" [ngClass]="{ 'sm:col-span-2': field.full }">
                    <span class="text-sm font-black text-aurora-ink dark:text-white">{{ field.label | t }}</span>
                    <input class="ui-input mt-2" [formControlName]="field.name" [type]="field.type" />
                  </label>
                }
              </div>

              @if (success()) {
                <p class="mt-5 rounded-ui bg-aurora-pine/10 px-3 py-2 text-sm font-bold text-aurora-pine dark:bg-aurora-pine/15 dark:text-aurora-pinebright">{{ 'checkout.success' | t }}</p>
              }

              <button class="ui-button ui-button-primary mt-6 w-full" type="submit" [disabled]="addressForm.invalid || confirming()">
                <lucide-icon [img]="PackageCheck" size="18" />
                {{ confirming() ? ('checkout.confirming' | t) : ('checkout.confirm' | t) }}
              </button>
            </form>

            <aside class="lg:sticky lg:top-28 lg:h-fit">
              <div class="surface-panel p-5">
                <div class="flex items-center gap-2">
                  <lucide-icon class="text-aurora-gold" [img]="CreditCard" size="20" />
                  <h2 class="text-xl font-black text-aurora-ink dark:text-white">{{ 'checkout.review' | t }}</h2>
                </div>

                <div class="mt-5 grid gap-3">
                  @for (item of data.items; track item.id) {
                    <div class="flex justify-between gap-4 rounded-ui bg-stone-50 p-3 text-sm dark:bg-white/5">
                      <div>
                        <p class="font-black text-aurora-ink dark:text-white">{{ item.productName }}</p>
                        <p class="mt-1 text-xs text-aurora-muted dark:text-stone-400">{{ item.quantity }} x {{ item.unitPrice | currency }}</p>
                      </div>
                      <p class="font-black text-aurora-ink dark:text-white">{{ item.lineTotal | currency }}</p>
                    </div>
                  }
                </div>

                <div class="mt-5 space-y-3 border-t border-aurora-line pt-4 text-sm dark:border-white/10">
                  <div class="flex justify-between text-aurora-muted dark:text-stone-300">
                    <span>{{ 'cart.subtotal' | t }}</span>
                    <span class="font-bold">{{ data.subtotal | currency }}</span>
                  </div>
                  <div class="flex justify-between text-aurora-muted dark:text-stone-300">
                    <span>{{ 'cart.discount' | t }}</span>
                    <span class="font-bold text-aurora-emerald">-{{ data.discountTotal | currency }}</span>
                  </div>
                  <div class="flex justify-between text-lg font-black text-aurora-ink dark:text-white">
                    <span>{{ 'cart.total' | t }}</span>
                    <span>{{ data.total | currency }}</span>
                  </div>
                </div>

                <div class="mt-5 rounded-ui bg-aurora-pine/10 p-3 text-sm font-semibold text-aurora-pine dark:bg-aurora-pine/15 dark:text-aurora-pinebright">
                  <lucide-icon class="inline-block align-[-3px]" [img]="ShieldCheck" size="17" />
                  {{ 'checkout.backendTrust' | t }}
                </div>
              </div>
            </aside>
          </div>
        }
      }
    </section>
  `
})
export class CheckoutPageComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly cartService = inject(CartService);
  private readonly checkoutService = inject(CheckoutService);
  private readonly language = inject(LanguageService);
  private readonly router = inject(Router);

  readonly cart = this.cartService.cart;
  readonly loading = signal(true);
  readonly confirming = signal(false);
  readonly success = signal(false);
  readonly error = signal<string | null>(null);

  readonly CheckCircle2 = CheckCircle2;
  readonly CreditCard = CreditCard;
  readonly MapPin = MapPin;
  readonly PackageCheck = PackageCheck;
  readonly ShieldCheck = ShieldCheck;

  readonly steps = [
    { key: 'address', label: 'checkout.address', copy: 'checkout.visualPrep' },
    { key: 'review', label: 'checkout.review', copy: 'checkout.backendTotals' },
    { key: 'payment', label: 'checkout.payment', copy: 'checkout.simulation' }
  ];

  readonly addressFields = [
    { name: 'fullName', label: 'checkout.fullName', type: 'text', full: true },
    { name: 'addressLine', label: 'checkout.addressLine', type: 'text', full: true },
    { name: 'city', label: 'checkout.city', type: 'text', full: false },
    { name: 'postalCode', label: 'checkout.postalCode', type: 'text', full: false },
    { name: 'country', label: 'checkout.country', type: 'text', full: false },
    { name: 'phone', label: 'checkout.phone', type: 'tel', full: false }
  ] as const;

  readonly addressForm = this.formBuilder.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(160)]],
    addressLine: ['', [Validators.required, Validators.maxLength(255)]],
    city: ['', [Validators.required, Validators.maxLength(120)]],
    postalCode: ['', [Validators.required, Validators.maxLength(40)]],
    country: ['', [Validators.required, Validators.maxLength(120)]],
    phone: ['', [Validators.required, Validators.maxLength(40)]]
  });

  ngOnInit(): void {
    this.cartService.loadCart().subscribe({
      next: () => this.loading.set(false),
      error: () => {
        this.error.set(this.language.translate('checkout.error'));
        this.loading.set(false);
      }
    });
  }

  confirm(cart: Cart): void {
    if (cart.items.length === 0 || this.addressForm.invalid) {
      this.addressForm.markAllAsTouched();
      return;
    }

    this.confirming.set(true);
    this.error.set(null);
    this.checkoutService.confirm().subscribe({
      next: (order) => {
        this.success.set(true);
        this.router.navigateByUrl(`/orders/${order.id}/payment`);
      },
      error: () => {
        this.error.set(this.language.translate('checkout.error'));
        this.confirming.set(false);
      }
    });
  }
}
