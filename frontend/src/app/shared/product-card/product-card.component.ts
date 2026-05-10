import { CurrencyPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, Heart, ShoppingCart, Star } from 'lucide-angular';
import { Product } from '../../core/models/product.model';

@Component({
  selector: 'app-product-card',
  imports: [CurrencyPipe, RouterLink, LucideAngularModule],
  template: `
    <article class="soft-card group overflow-hidden">
      <a [routerLink]="['/products', product().slug]" class="block cursor-pointer">
        <div class="aspect-[4/3] overflow-hidden bg-slate-100 dark:bg-white/10">
          <img
            class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03] motion-reduce:transition-none motion-reduce:group-hover:scale-100"
            [src]="imageUrl()"
            [alt]="product().name"
          />
        </div>
      </a>
      <div class="p-4">
        <div class="flex items-center justify-between gap-3">
          <p class="text-xs font-semibold uppercase tracking-[0.14em] text-aurora-ocean">
            {{ product().brand.name }}
          </p>
          <div class="flex items-center gap-1 text-xs font-semibold text-amber-600">
            <lucide-icon [img]="Star" size="14" />
            4.8
          </div>
        </div>
        <a [routerLink]="['/products', product().slug]" class="mt-2 line-clamp-2 block cursor-pointer text-base font-bold text-slate-950 hover:text-aurora-ocean dark:text-white">
          {{ product().name }}
        </a>
        <p class="mt-2 line-clamp-2 min-h-10 text-sm leading-5 text-slate-600 dark:text-slate-300">
          {{ product().shortDescription || 'Premium marketplace item ready for Aurora checkout.' }}
        </p>
        <div class="mt-4 flex items-center justify-between gap-3">
          <div>
            <p class="text-xs text-slate-500 dark:text-slate-400">From</p>
            <p class="text-lg font-bold text-slate-950 dark:text-white">{{ product().basePrice | currency }}</p>
          </div>
          <div class="flex items-center gap-2">
            <button class="ui-button h-10 w-10 border border-slate-200 bg-white p-0 text-slate-700 hover:bg-slate-50 dark:border-white/10 dark:bg-white/10 dark:text-white" type="button" aria-label="Save item">
              <lucide-icon [img]="Heart" size="17" />
            </button>
            <button class="ui-button ui-button-primary h-10 w-10 p-0" type="button" aria-label="Add item to cart">
              <lucide-icon [img]="ShoppingCart" size="17" />
            </button>
          </div>
        </div>
      </div>
    </article>
  `
})
export class ProductCardComponent {
  readonly product = input.required<Product>();
  readonly Heart = Heart;
  readonly ShoppingCart = ShoppingCart;
  readonly Star = Star;

  imageUrl(): string {
    return (
      this.product().images.find((image) => image.mainImage)?.url ||
      this.product().images[0]?.url ||
      'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=900&q=80'
    );
  }
}
