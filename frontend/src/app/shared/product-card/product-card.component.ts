import { CurrencyPipe } from '@angular/common';
import { Component, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowUpRight, Heart, ShoppingBag, Star } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { Product } from '../../core/models/product.model';
import { AuthService } from '../../services/auth.service';
import { CartService } from '../../services/cart.service';
import { WishlistService } from '../../services/wishlist.service';

@Component({
  selector: 'app-product-card',
  imports: [CurrencyPipe, RouterLink, LucideAngularModule, TranslatePipe],
  template: `
    <article class="soft-card group overflow-hidden">
      <a [routerLink]="['/products', product().slug]" class="relative block cursor-pointer">
        <div class="aspect-[4/3] overflow-hidden bg-stone-100 dark:bg-white/10">
            <img
              loading="lazy"
              class="h-full w-full object-cover transition duration-500 group-hover:scale-[1.04] motion-reduce:transition-none motion-reduce:group-hover:scale-100"
            [src]="imageUrl()"
            [alt]="product().name"
          />
          <div class="absolute inset-0 bg-gradient-to-t from-aurora-night/35 via-transparent to-transparent opacity-70"></div>
          <div class="absolute left-3 top-3 flex flex-wrap gap-2">
            @if (product().featured) {
              <span class="aurora-badge border-white/60 bg-white/90 text-aurora-ink">{{ 'product.featured' | t }}</span>
            }
            <span class="aurora-badge border-white/60 bg-white/90 text-aurora-ink">{{ product().category.name }}</span>
          </div>
          <span class="absolute bottom-3 right-3 flex h-10 w-10 items-center justify-center rounded-ui border border-white/60 bg-white/90 text-aurora-ink shadow-sm transition duration-200 group-hover:bg-aurora-ink group-hover:text-white dark:border-white/10 dark:bg-white/15 dark:text-white">
            <lucide-icon [img]="ArrowUpRight" size="18" />
          </span>
        </div>
      </a>
      <div class="p-4 sm:p-5">
        <div class="flex items-center justify-between gap-3">
          <p class="text-xs font-black uppercase tracking-[0.14em] text-aurora-gold dark:text-amber-300">
            {{ product().brand.name }}
          </p>
          <div class="flex items-center gap-1 text-xs font-bold text-amber-700 dark:text-amber-300">
            <lucide-icon [img]="Star" size="14" />
            4.8
          </div>
        </div>
        <a [routerLink]="['/products', product().slug]" class="mt-2 line-clamp-2 block cursor-pointer text-lg font-black leading-6 text-aurora-ink transition-colors duration-200 hover:text-aurora-gold dark:text-white dark:hover:text-amber-300">
          {{ product().name }}
        </a>
        <p class="mt-2 line-clamp-2 min-h-10 text-sm leading-5 text-aurora-muted dark:text-stone-300">
          {{ product().shortDescription || 'A premium piece from the Aurora edit.' }}
        </p>
        <div class="mt-5 flex items-end justify-between gap-3">
          <div>
            <p class="text-xs font-semibold text-aurora-muted dark:text-stone-400">{{ 'common.from' | t }}</p>
            <p class="text-2xl font-black text-aurora-ink dark:text-white">{{ product().basePrice | currency }}</p>
          </div>
          <div class="flex items-center gap-2">
            <button class="ui-button h-10 w-10 p-0" [class.ui-button-primary]="wishlist.isWishlisted(product().id)" [class.ui-button-secondary]="!wishlist.isWishlisted(product().id)" type="button" aria-label="Save item" [disabled]="savingWishlist()" (click)="toggleWishlist()">
              <lucide-icon [img]="Heart" size="17" />
            </button>
            <button class="ui-button ui-button-primary h-10 w-10 p-0" type="button" aria-label="Add item to cart" [disabled]="addingCart() || !firstVariantId()" (click)="addToCart()">
              <lucide-icon [img]="ShoppingBag" size="17" />
            </button>
          </div>
        </div>
      </div>
    </article>
  `
})
export class ProductCardComponent {
  readonly product = input.required<Product>();
  readonly addingCart = signal(false);
  readonly savingWishlist = signal(false);
  readonly ArrowUpRight = ArrowUpRight;
  readonly Heart = Heart;
  readonly ShoppingBag = ShoppingBag;
  readonly Star = Star;

  constructor(
    private readonly auth: AuthService,
    private readonly cartService: CartService,
    readonly wishlist: WishlistService,
    private readonly router: Router
  ) {}

  addToCart(): void {
    const variantId = this.firstVariantId();
    if (!variantId) {
      return;
    }

    if (!this.auth.isAuthenticated()) {
      this.router.navigateByUrl('/login');
      return;
    }

    this.addingCart.set(true);
    this.cartService.addItem({ variantId, quantity: 1 }).subscribe({
      next: () => this.addingCart.set(false),
      error: () => this.addingCart.set(false)
    });
  }

  toggleWishlist(): void {
    if (!this.auth.isAuthenticated()) {
      this.router.navigateByUrl('/login');
      return;
    }

    this.savingWishlist.set(true);

    if (this.wishlist.isWishlisted(this.product().id)) {
      this.wishlist.remove(this.product().id).subscribe({
        next: () => this.savingWishlist.set(false),
        error: () => this.savingWishlist.set(false)
      });
      return;
    }

    this.wishlist.add(this.product().id).subscribe({
      next: () => this.savingWishlist.set(false),
      error: () => this.savingWishlist.set(false)
    });
  }

  firstVariantId(): string | null {
    return this.product().variants.find((variant) => variant.active)?.id ?? this.product().variants[0]?.id ?? null;
  }

  imageUrl(): string {
    return (
      this.product().images.find((image) => image.mainImage)?.url ||
      this.product().images[0]?.url ||
      'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=900&q=80'
    );
  }
}
