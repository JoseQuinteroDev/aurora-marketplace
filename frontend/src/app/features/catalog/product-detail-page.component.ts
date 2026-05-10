import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Heart, ShieldCheck, ShoppingCart, Truck } from 'lucide-angular';
import { Product } from '../../core/models/product.model';
import { CatalogService } from '../../services/catalog.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-product-detail-page',
  imports: [CurrencyPipe, RouterLink, LucideAngularModule, StatePanelComponent],
  template: `
    <section class="page-shell py-10">
      <a routerLink="/catalog" class="inline-flex cursor-pointer items-center gap-2 text-sm font-semibold text-slate-600 hover:text-aurora-ocean dark:text-slate-300">
        <lucide-icon [img]="ArrowLeft" size="17" />
        Back to catalog
      </a>

      @if (loading()) {
        <div class="mt-8 grid gap-8 lg:grid-cols-2">
          <div class="aspect-square animate-pulse rounded-ui bg-slate-200 dark:bg-white/10"></div>
          <div class="space-y-4">
            <div class="h-4 w-32 animate-pulse rounded bg-slate-200 dark:bg-white/10"></div>
            <div class="h-12 w-3/4 animate-pulse rounded bg-slate-200 dark:bg-white/10"></div>
            <div class="h-24 w-full animate-pulse rounded bg-slate-200 dark:bg-white/10"></div>
          </div>
        </div>
      } @else if (error()) {
        <div class="mt-8">
          <app-state-panel mode="error" title="Product unavailable" [message]="error()!" />
        </div>
      } @else if (product(); as item) {
        <div class="mt-8 grid gap-8 lg:grid-cols-[0.95fr_1.05fr]">
          <div class="overflow-hidden rounded-ui border border-slate-200 bg-white shadow-premium dark:border-white/10 dark:bg-white/[0.06]">
            <img class="aspect-square w-full object-cover" [src]="imageUrl(item)" [alt]="item.name" />
          </div>

          <div>
            <p class="section-kicker">{{ item.brand.name }} / {{ item.category.name }}</p>
            <h1 class="mt-3 text-4xl font-black tracking-normal text-slate-950 sm:text-5xl dark:text-white">{{ item.name }}</h1>
            <p class="mt-5 max-w-2xl text-base leading-8 text-slate-600 dark:text-slate-300">
              {{ item.description || item.shortDescription || 'A premium Aurora marketplace product prepared for cart and checkout flows.' }}
            </p>

            <div class="mt-8 flex flex-wrap items-center gap-4">
              <p class="text-3xl font-black text-slate-950 dark:text-white">{{ item.basePrice | currency }}</p>
              <span class="rounded-ui bg-emerald-50 px-3 py-2 text-sm font-bold text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">In catalog</span>
            </div>

            <div class="mt-8 grid gap-3 sm:grid-cols-3">
              <div class="rounded-ui border border-slate-200 bg-white p-4 dark:border-white/10 dark:bg-white/[0.06]">
                <lucide-icon class="text-aurora-ocean" [img]="Truck" size="20" />
                <p class="mt-3 text-sm font-bold">Fast fulfillment</p>
              </div>
              <div class="rounded-ui border border-slate-200 bg-white p-4 dark:border-white/10 dark:bg-white/[0.06]">
                <lucide-icon class="text-aurora-ocean" [img]="ShieldCheck" size="20" />
                <p class="mt-3 text-sm font-bold">Backend priced</p>
              </div>
              <div class="rounded-ui border border-slate-200 bg-white p-4 dark:border-white/10 dark:bg-white/[0.06]">
                <lucide-icon class="text-aurora-ocean" [img]="Heart" size="20" />
                <p class="mt-3 text-sm font-bold">Wishlist ready</p>
              </div>
            </div>

            <div class="mt-8 flex flex-col gap-3 sm:flex-row">
              <button class="ui-button ui-button-primary" type="button">
                <lucide-icon [img]="ShoppingCart" size="18" />
                Add to cart
              </button>
              <button class="ui-button ui-button-secondary" type="button">
                <lucide-icon [img]="Heart" size="18" />
                Save item
              </button>
            </div>

            <div class="mt-8">
              <p class="text-sm font-bold text-slate-950 dark:text-white">Available variants</p>
              <div class="mt-3 flex flex-wrap gap-2">
                @for (variant of item.variants; track variant.id) {
                  <span class="rounded-ui border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 dark:border-white/10 dark:bg-white/10 dark:text-white">{{ variant.name }}</span>
                } @empty {
                  <span class="text-sm text-slate-500">No variants configured yet.</span>
                }
              </div>
            </div>
          </div>
        </div>
      }
    </section>
  `
})
export class ProductDetailPageComponent implements OnInit {
  readonly product = signal<Product | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly ArrowLeft = ArrowLeft;
  readonly Heart = Heart;
  readonly ShieldCheck = ShieldCheck;
  readonly ShoppingCart = ShoppingCart;
  readonly Truck = Truck;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly catalogService: CatalogService
  ) {}

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug');

    if (!slug) {
      this.error.set('Missing product slug.');
      this.loading.set(false);
      return;
    }

    this.catalogService.getProduct(slug).subscribe({
      next: (product) => {
        this.product.set(product);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('The product could not be loaded.');
        this.loading.set(false);
      }
    });
  }

  imageUrl(product: Product): string {
    return (
      product.images.find((image) => image.mainImage)?.url ||
      product.images[0]?.url ||
      'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=1200&q=85'
    );
  }
}
