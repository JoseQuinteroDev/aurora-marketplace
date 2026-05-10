import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  LucideAngularModule,
  ArrowLeft,
  BadgeCheck,
  Heart,
  ListChecks,
  MessageSquareText,
  ShieldCheck,
  ShoppingBag,
  Star,
  Truck,
  Zap
} from 'lucide-angular';
import { Product } from '../../core/models/product.model';
import { CatalogService } from '../../services/catalog.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

type ProductTab = 'description' | 'specs' | 'reviews';

@Component({
  selector: 'app-product-detail-page',
  imports: [CurrencyPipe, RouterLink, LucideAngularModule, StatePanelComponent],
  template: `
    <section class="page-shell py-10 sm:py-12">
      <a routerLink="/catalog" class="premium-link inline-flex items-center gap-2 text-sm">
        <lucide-icon [img]="ArrowLeft" size="17" />
        Back to catalog
      </a>

      @if (loading()) {
        <div class="mt-8 grid gap-8 lg:grid-cols-[0.95fr_1.05fr]">
          <div class="skeleton-line aspect-square rounded-ui"></div>
          <div class="space-y-4">
            <div class="skeleton-line h-4 w-32"></div>
            <div class="skeleton-line h-12 w-3/4"></div>
            <div class="skeleton-line h-24 w-full"></div>
            <div class="skeleton-line h-14 w-full"></div>
          </div>
        </div>
      } @else if (error()) {
        <div class="mt-8">
          <app-state-panel mode="error" title="Product unavailable" [message]="error()!" />
        </div>
      } @else if (product(); as item) {
        <div class="mt-8 grid gap-8 lg:grid-cols-[0.95fr_1.05fr]">
          <div class="space-y-4">
            <div class="premium-shell relative overflow-hidden p-3">
              <img class="aspect-square w-full rounded-ui object-cover" [src]="selectedImage() || imageUrl(item)" [alt]="item.name" />
              <div class="absolute left-6 top-6 flex flex-wrap gap-2">
                @if (item.featured) {
                  <span class="aurora-badge border-white/60 bg-white/90 text-aurora-ink">Featured</span>
                }
                <span class="aurora-badge border-white/60 bg-white/90 text-aurora-ink">{{ item.category.name }}</span>
              </div>
            </div>

            <div class="grid grid-cols-4 gap-3">
              @for (image of galleryUrls(item); track image) {
                <button class="group overflow-hidden rounded-ui border border-aurora-line bg-white p-1 transition duration-200 hover:border-aurora-amber dark:border-white/10 dark:bg-white/10" type="button" (click)="selectedImage.set(image)" aria-label="Select product image">
                  <img class="aspect-square w-full rounded-ui object-cover transition duration-300 group-hover:scale-[1.04] motion-reduce:transition-none motion-reduce:group-hover:scale-100" [src]="image" [alt]="item.name" />
                </button>
              }
            </div>
          </div>

          <div>
            <div class="lg:sticky lg:top-28">
              <div class="space-y-5">
                <div>
                  <p class="section-kicker">{{ item.brand.name }} / {{ item.category.name }}</p>
                  <h1 class="mt-3 text-4xl font-black leading-tight text-aurora-ink sm:text-5xl dark:text-white">{{ item.name }}</h1>
                  <div class="mt-4 flex flex-wrap items-center gap-3">
                    <span class="inline-flex items-center gap-1.5 text-sm font-black text-amber-700 dark:text-amber-300">
                      <lucide-icon [img]="Star" size="16" />
                      4.8
                    </span>
                    <span class="h-1 w-1 rounded-full bg-aurora-line dark:bg-white/20"></span>
                    <span class="text-sm font-semibold text-aurora-muted dark:text-stone-300">{{ activeVariantCount(item) }} variants</span>
                    <span class="aurora-badge bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">
                      <lucide-icon [img]="BadgeCheck" size="14" />
                      In stock
                    </span>
                  </div>
                </div>

                <div class="surface-panel p-5">
                  <div class="flex flex-wrap items-end justify-between gap-4">
                    <div>
                      <p class="text-sm font-semibold text-aurora-muted dark:text-stone-400">Starting at</p>
                      <p class="mt-1 text-4xl font-black text-aurora-ink dark:text-white">{{ item.basePrice | currency }}</p>
                    </div>
                    <button class="ui-button ui-button-secondary h-11 w-11 min-h-11 p-0" type="button" aria-label="Save item">
                      <lucide-icon [img]="Heart" size="18" />
                    </button>
                  </div>

                  <div class="mt-5 grid gap-3 sm:grid-cols-2">
                    <button class="ui-button ui-button-primary w-full" type="button">
                      <lucide-icon [img]="ShoppingBag" size="18" />
                      Add to cart
                    </button>
                    <button class="ui-button ui-button-accent w-full" type="button">
                      <lucide-icon [img]="Zap" size="18" />
                      Buy now
                    </button>
                  </div>
                </div>

                <div class="surface-panel p-5">
                  <p class="text-sm font-black text-aurora-ink dark:text-white">Choose a variant</p>
                  <div class="mt-3 grid gap-2 sm:grid-cols-2">
                    @for (variant of item.variants; track variant.id) {
                      <button class="rounded-ui border border-aurora-line bg-white p-3 text-left transition duration-200 hover:border-aurora-amber hover:shadow-sm dark:border-white/10 dark:bg-white/10" type="button">
                        <span class="block text-sm font-black text-aurora-ink dark:text-white">{{ variant.name }}</span>
                        <span class="mt-1 block text-xs font-semibold text-aurora-muted dark:text-stone-400">{{ variant.sku }}</span>
                        <span class="mt-2 block text-sm font-black text-aurora-gold dark:text-amber-300">{{ variant.effectivePrice | currency }}</span>
                      </button>
                    } @empty {
                      <p class="text-sm text-aurora-muted dark:text-stone-300">Default configuration available.</p>
                    }
                  </div>
                </div>

                <div class="grid gap-3 sm:grid-cols-3">
                  @for (promise of promises; track promise.title) {
                    <div class="surface-panel p-4">
                      <lucide-icon class="text-aurora-ocean dark:text-blue-300" [img]="promise.icon" size="20" />
                      <p class="mt-3 text-sm font-black text-aurora-ink dark:text-white">{{ promise.title }}</p>
                      <p class="mt-1 text-xs leading-5 text-aurora-muted dark:text-stone-300">{{ promise.copy }}</p>
                    </div>
                  }
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="mt-10 surface-panel p-3 sm:p-4">
          <div class="grid gap-2 sm:grid-cols-3">
            <button class="ui-button justify-start" [class.ui-button-primary]="activeTab() === 'description'" [class.ui-button-secondary]="activeTab() !== 'description'" type="button" (click)="activeTab.set('description')">
              <lucide-icon [img]="MessageSquareText" size="17" />
              Description
            </button>
            <button class="ui-button justify-start" [class.ui-button-primary]="activeTab() === 'specs'" [class.ui-button-secondary]="activeTab() !== 'specs'" type="button" (click)="activeTab.set('specs')">
              <lucide-icon [img]="ListChecks" size="17" />
              Specifications
            </button>
            <button class="ui-button justify-start" [class.ui-button-primary]="activeTab() === 'reviews'" [class.ui-button-secondary]="activeTab() !== 'reviews'" type="button" (click)="activeTab.set('reviews')">
              <lucide-icon [img]="Star" size="17" />
              Reviews
            </button>
          </div>

          <div class="p-3 sm:p-5">
            @if (activeTab() === 'description') {
              <p class="max-w-3xl text-sm leading-7 text-aurora-muted dark:text-stone-300">
                {{ item.description || item.shortDescription || 'A refined Aurora marketplace product with clean detail structure, visual hierarchy and purchase CTAs ready for cart integration.' }}
              </p>
            } @else if (activeTab() === 'specs') {
              <div class="grid gap-3 sm:grid-cols-3">
                <div class="rounded-ui bg-stone-50 p-4 dark:bg-white/5">
                  <p class="text-xs font-bold uppercase tracking-[0.12em] text-aurora-muted dark:text-stone-400">Brand</p>
                  <p class="mt-2 font-black text-aurora-ink dark:text-white">{{ item.brand.name }}</p>
                </div>
                <div class="rounded-ui bg-stone-50 p-4 dark:bg-white/5">
                  <p class="text-xs font-bold uppercase tracking-[0.12em] text-aurora-muted dark:text-stone-400">Category</p>
                  <p class="mt-2 font-black text-aurora-ink dark:text-white">{{ item.category.name }}</p>
                </div>
                <div class="rounded-ui bg-stone-50 p-4 dark:bg-white/5">
                  <p class="text-xs font-bold uppercase tracking-[0.12em] text-aurora-muted dark:text-stone-400">Status</p>
                  <p class="mt-2 font-black text-aurora-ink dark:text-white">{{ item.active ? 'Active' : 'Inactive' }}</p>
                </div>
              </div>
            } @else {
              <app-state-panel title="Reviews are coming" message="The backend review endpoint is ready for the next product detail iteration." />
            }
          </div>
        </div>
      }
    </section>
  `
})
export class ProductDetailPageComponent implements OnInit {
  readonly product = signal<Product | null>(null);
  readonly selectedImage = signal<string | null>(null);
  readonly activeTab = signal<ProductTab>('description');
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly ArrowLeft = ArrowLeft;
  readonly BadgeCheck = BadgeCheck;
  readonly Heart = Heart;
  readonly ListChecks = ListChecks;
  readonly MessageSquareText = MessageSquareText;
  readonly ShieldCheck = ShieldCheck;
  readonly ShoppingBag = ShoppingBag;
  readonly Star = Star;
  readonly Truck = Truck;
  readonly Zap = Zap;

  readonly promises = [
    { icon: Truck, title: 'Fast shipping', copy: 'Clear delivery expectation.' },
    { icon: ShieldCheck, title: 'Secure checkout', copy: 'Protected account flow.' },
    { icon: BadgeCheck, title: 'Quality pick', copy: 'Curated catalog signal.' }
  ];

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
        this.selectedImage.set(this.imageUrl(product));
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

  galleryUrls(product: Product): string[] {
    const urls = product.images.map((image) => image.url).filter(Boolean);
    if (urls.length > 0) {
      return urls.slice(0, 4);
    }

    return [
      this.imageUrl(product),
      'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=900&q=80',
      'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80',
      'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=900&q=80'
    ];
  }

  activeVariantCount(product: Product): number {
    return product.variants.filter((variant) => variant.active).length || 1;
  }
}
