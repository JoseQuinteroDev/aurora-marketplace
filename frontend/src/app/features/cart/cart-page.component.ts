import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, BadgePercent, Minus, Plus, ShoppingBag, Trash2 } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
import { Cart, CartItem } from '../../core/models/cart.model';
import { cartErrorKey } from '../../core/util/cart-errors';
import { CartService } from '../../services/cart.service';
import { ToastService } from '../../services/toast.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-cart-page',
  imports: [CurrencyPipe, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell py-10 sm:py-12">
      <div class="premium-shell overflow-hidden p-6 sm:p-8">
        <p class="section-kicker">{{ 'nav.cart' | t }}</p>
        <h1 class="mt-3 text-4xl font-black leading-tight text-aurora-ink sm:text-5xl dark:text-white">{{ 'cart.title' | t }}</h1>
        <p class="mt-4 max-w-2xl text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'cart.subtitle' | t }}</p>
      </div>

      @if (loading()) {
        <div class="mt-8 grid gap-6 lg:grid-cols-[1fr_360px]">
          <div class="space-y-4">
            @for (item of [1, 2, 3]; track item) {
              <div class="surface-panel p-4">
                <div class="flex gap-4">
                  <div class="skeleton-line h-24 w-24 shrink-0 rounded-ui"></div>
                  <div class="flex-1 space-y-3">
                    <div class="skeleton-line h-5 w-2/3"></div>
                    <div class="skeleton-line h-4 w-1/2"></div>
                    <div class="skeleton-line h-8 w-36"></div>
                  </div>
                </div>
              </div>
            }
          </div>
          <div class="skeleton-line h-72 rounded-ui"></div>
        </div>
      } @else if (error()) {
        <div class="mt-8">
          <app-state-panel mode="error" title="{{ 'cart.error' | t }}" [message]="error()!" />
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
            <div class="space-y-4">
              @for (item of data.items; track item.id) {
                <article class="surface-panel p-4">
                  <div class="grid gap-4 sm:grid-cols-[112px_1fr_auto] sm:items-center">
                    <a [routerLink]="['/products', item.productSlug]" class="block overflow-hidden rounded-ui bg-stone-100 dark:bg-white/10">
                      <img loading="lazy" class="aspect-square w-full object-cover" src="https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=500&q=75" [alt]="item.productName" />
                    </a>
                    <div>
                      <a [routerLink]="['/products', item.productSlug]" class="cursor-pointer text-lg font-black text-aurora-ink transition-colors duration-200 hover:text-aurora-gold dark:text-white">{{ item.productName }}</a>
                      <p class="mt-1 text-sm font-semibold text-aurora-muted dark:text-stone-300">{{ item.variantName }} / {{ item.variantSku }}</p>
                      <p class="mt-3 text-sm text-aurora-muted dark:text-stone-400">{{ item.unitPrice | currency }} x {{ item.quantity }}</p>
                    </div>
                    <div class="flex items-center justify-between gap-4 sm:block sm:text-right">
                      <p class="text-xl font-black text-aurora-ink dark:text-white">{{ item.lineTotal | currency }}</p>
                      <div class="mt-0 flex items-center gap-2 sm:mt-4">
                        <button class="ui-button ui-button-secondary h-10 w-10 min-h-10 p-0" type="button" [disabled]="actionId() === item.id || item.quantity <= 1" (click)="changeQuantity(item, item.quantity - 1)" aria-label="Decrease quantity">
                          <lucide-icon [img]="Minus" size="16" />
                        </button>
                        <span class="flex h-10 min-w-10 items-center justify-center rounded-ui border border-aurora-line bg-white px-3 text-sm font-black dark:border-white/10 dark:bg-white/10">{{ item.quantity }}</span>
                        <button class="ui-button ui-button-secondary h-10 w-10 min-h-10 p-0" type="button" [disabled]="actionId() === item.id" (click)="changeQuantity(item, item.quantity + 1)" aria-label="Increase quantity">
                          <lucide-icon [img]="Plus" size="16" />
                        </button>
                        <button class="ui-button ui-button-secondary h-10 w-10 min-h-10 p-0 text-aurora-rose" type="button" [disabled]="actionId() === item.id" (click)="removeItem(item)" [attr.aria-label]="'cart.remove' | t">
                          <lucide-icon [img]="Trash2" size="16" />
                        </button>
                      </div>
                    </div>
                  </div>
                </article>
              }
              <button class="ui-button ui-button-secondary" type="button" [disabled]="actionId() === 'clear'" (click)="clearCart()">
                <lucide-icon [img]="Trash2" size="17" />
                {{ 'cart.clear' | t }}
              </button>
            </div>

            <aside class="lg:sticky lg:top-28 lg:h-fit">
              <div class="surface-panel p-5">
                <div class="flex items-center gap-2">
                  <lucide-icon class="text-aurora-gold" [img]="ShoppingBag" size="20" />
                  <h2 class="text-xl font-black text-aurora-ink dark:text-white">{{ 'cart.summary' | t }}</h2>
                </div>

                <div class="mt-5 space-y-3 text-sm">
                  <div class="flex justify-between gap-4 text-aurora-muted dark:text-stone-300">
                    <span>{{ 'cart.subtotal' | t }}</span>
                    <span class="font-bold">{{ data.subtotal | currency }}</span>
                  </div>
                  <div class="flex justify-between gap-4 text-aurora-muted dark:text-stone-300">
                    <span>{{ 'cart.discount' | t }}</span>
                    <span class="font-bold text-aurora-emerald">-{{ data.discountTotal | currency }}</span>
                  </div>
                  <div class="border-t border-aurora-line pt-3 text-lg font-black text-aurora-ink dark:border-white/10 dark:text-white">
                    <div class="flex justify-between gap-4">
                      <span>{{ 'cart.total' | t }}</span>
                      <span>{{ data.total | currency }}</span>
                    </div>
                  </div>
                </div>

                <div class="mt-6 rounded-ui border border-aurora-line bg-white/70 p-3 dark:border-white/10 dark:bg-white/5">
                  <label class="text-sm font-black text-aurora-ink dark:text-white">{{ 'cart.coupon' | t }}</label>
                  @if (data.coupon) {
                    <div class="mt-3 flex items-center justify-between gap-3 rounded-ui bg-emerald-50 px-3 py-2 text-sm font-black text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">
                      <span>{{ data.coupon.code }}</span>
                      <button class="cursor-pointer text-xs underline" type="button" [disabled]="actionId() === 'coupon'" (click)="removeCoupon()">{{ 'cart.removeCoupon' | t }}</button>
                    </div>
                  } @else {
                    <div class="mt-2 flex gap-2">
                      <input class="ui-input" [value]="couponCode()" (input)="couponCode.set($any($event.target).value)" [placeholder]="'cart.couponPlaceholder' | t" />
                      <button class="ui-button ui-button-secondary" type="button" [disabled]="actionId() === 'coupon' || !couponCode().trim()" (click)="applyCoupon()">
                        <lucide-icon [img]="BadgePercent" size="17" />
                        {{ 'cart.applyCoupon' | t }}
                      </button>
                    </div>
                  }
                </div>

                <a routerLink="/checkout" class="ui-button ui-button-primary mt-5 w-full">{{ 'cart.checkout' | t }}</a>
              </div>
            </aside>
          </div>
        }
      }
    </section>
  `
})
export class CartPageComponent implements OnInit {
  private readonly cartService = inject(CartService);
  private readonly language = inject(LanguageService);
  private readonly toast = inject(ToastService);

  readonly cart = this.cartService.cart;
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly actionId = signal<string | null>(null);
  readonly couponCode = signal('');

  readonly BadgePercent = BadgePercent;
  readonly Minus = Minus;
  readonly Plus = Plus;
  readonly ShoppingBag = ShoppingBag;
  readonly Trash2 = Trash2;

  ngOnInit(): void {
    this.cartService.loadCart().subscribe({
      next: () => this.loading.set(false),
      error: () => {
        this.error.set(this.language.translate('cart.error'));
        this.loading.set(false);
      }
    });
  }

  changeQuantity(item: CartItem, quantity: number): void {
    if (quantity < 1) {
      return;
    }

    this.actionId.set(item.id);
    this.cartService.updateItem(item.id, { quantity }).subscribe({
      next: () => this.actionId.set(null),
      error: (err) => {
        this.actionId.set(null);
        this.toast.error(this.language.translate(cartErrorKey(err)));
      }
    });
  }

  removeItem(item: CartItem): void {
    this.actionId.set(item.id);
    this.cartService.removeItem(item.id).subscribe({
      next: () => {
        this.actionId.set(null);
        this.toast.success(this.language.translate('cart.toast.removed'));
      },
      error: (err) => {
        this.actionId.set(null);
        this.toast.error(this.language.translate(cartErrorKey(err)));
      }
    });
  }

  clearCart(): void {
    this.actionId.set('clear');
    this.cartService.clearCart().subscribe({
      next: () => {
        this.actionId.set(null);
        this.toast.success(this.language.translate('cart.toast.cleared'));
      },
      error: (err) => {
        this.actionId.set(null);
        this.toast.error(this.language.translate(cartErrorKey(err)));
      }
    });
  }

  applyCoupon(): void {
    this.actionId.set('coupon');
    this.cartService.applyCoupon({ code: this.couponCode().trim() }).subscribe({
      next: () => {
        this.couponCode.set('');
        this.actionId.set(null);
        this.toast.success(this.language.translate('cart.toast.couponApplied'));
      },
      error: () => {
        this.actionId.set(null);
        this.toast.error(this.language.translate('cart.toast.couponError'));
      }
    });
  }

  removeCoupon(): void {
    this.actionId.set('coupon');
    this.cartService.removeCoupon().subscribe({
      next: () => {
        this.actionId.set(null);
        this.toast.info(this.language.translate('cart.toast.couponRemoved'));
      },
      error: () => this.actionId.set(null)
    });
  }
}
