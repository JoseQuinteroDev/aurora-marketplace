import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowUpRight, Heart, Trash2 } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
import { WishlistItem } from '../../core/models/wishlist.model';
import { PRODUCT_IMAGE_PLACEHOLDER } from '../../core/util/product-media';
import { WishlistService } from '../../services/wishlist.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-wishlist-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell py-10 sm:py-12">
      <div class="premium-shell overflow-hidden p-6 sm:p-8">
        <p class="section-kicker">{{ 'nav.wishlist' | t }}</p>
        <h1 class="mt-3 text-4xl font-semibold leading-tight text-aurora-ink sm:text-5xl dark:text-white">{{ 'wishlist.title' | t }}</h1>
        <p class="mt-4 max-w-2xl text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'wishlist.subtitle' | t }}</p>
      </div>

      @if (loading()) {
        <div class="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          @for (item of [1, 2, 3]; track item) {
            <div class="soft-card overflow-hidden">
              <div class="skeleton-line aspect-[4/3] rounded-none"></div>
              <div class="space-y-3 p-5">
                <div class="skeleton-line h-5 w-3/4"></div>
                <div class="skeleton-line h-4 w-1/2"></div>
                <div class="skeleton-line h-10 w-full"></div>
              </div>
            </div>
          }
        </div>
      } @else if (error()) {
        <div class="mt-8">
          <app-state-panel mode="error" title="{{ 'common.error' | t }}" [message]="error()!" />
          <div class="mt-6 text-center">
            <button class="ui-button ui-button-primary" type="button" (click)="reload()">{{ 'common.retry' | t }}</button>
          </div>
        </div>
      } @else if (items().length === 0) {
        <div class="mt-8">
          <app-state-panel title="{{ 'wishlist.empty' | t }}" message="{{ 'wishlist.emptyMessage' | t }}" />
          <div class="mt-6 text-center">
            <a routerLink="/catalog" class="ui-button ui-button-primary">{{ 'cart.keepShopping' | t }}</a>
          </div>
        </div>
      } @else {
        <div class="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          @for (item of items(); track item.id) {
            <article class="soft-card group overflow-hidden">
              <a [routerLink]="['/products', item.productSlug]" class="relative block cursor-pointer">
                <img loading="lazy" class="aspect-[4/3] w-full object-cover transition duration-500 group-hover:scale-[1.04] motion-reduce:transition-none motion-reduce:group-hover:scale-100" [src]="placeholder" [alt]="item.productName" />
                <div class="absolute inset-0 bg-gradient-to-t from-aurora-night/45 via-transparent to-transparent"></div>
                <span class="absolute left-3 top-3 aurora-badge border-white/60 bg-white/90 text-aurora-ink">
                  <lucide-icon [img]="Heart" size="14" />
                  {{ item.createdAt | date:'mediumDate' }}
                </span>
              </a>
              <div class="p-5">
                <a [routerLink]="['/products', item.productSlug]" class="cursor-pointer text-xl font-extrabold text-aurora-ink transition-colors duration-200 hover:text-aurora-gold dark:text-white">{{ item.productName }}</a>
                <p class="mt-3 text-2xl font-extrabold text-aurora-ink dark:text-white">{{ item.basePrice | currency }}</p>
                <div class="mt-5 flex gap-2">
                  <a [routerLink]="['/products', item.productSlug]" class="ui-button ui-button-primary flex-1">
                    {{ 'common.view' | t }}
                    <lucide-icon [img]="ArrowUpRight" size="17" />
                  </a>
                  <button class="ui-button ui-button-secondary h-11 w-11 p-0 text-aurora-rose" type="button" [disabled]="removingId() === item.productId" (click)="remove(item)" [attr.aria-label]="'wishlist.remove' | t">
                    <lucide-icon [img]="Trash2" size="17" />
                  </button>
                </div>
              </div>
            </article>
          }
        </div>
      }
    </section>
  `
})
export class WishlistPageComponent implements OnInit {
  private readonly language = inject(LanguageService);
  private readonly wishlistService = inject(WishlistService);

  readonly items = this.wishlistService.wishlist;
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly removingId = signal<string | null>(null);

  readonly ArrowUpRight = ArrowUpRight;
  readonly Heart = Heart;
  readonly Trash2 = Trash2;
  // Wishlist items carry no image; show the branded placeholder, not an unrelated stock photo.
  readonly placeholder = PRODUCT_IMAGE_PLACEHOLDER;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.wishlistService.loadWishlist().subscribe({
      next: () => this.loading.set(false),
      error: () => {
        this.error.set(this.language.translate('wishlist.loadError'));
        this.loading.set(false);
      }
    });
  }

  remove(item: WishlistItem): void {
    this.removingId.set(item.productId);
    this.wishlistService.remove(item.productId).subscribe({
      next: () => this.removingId.set(null),
      error: () => this.removingId.set(null)
    });
  }
}
