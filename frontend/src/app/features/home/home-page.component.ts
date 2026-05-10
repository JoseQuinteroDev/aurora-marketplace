import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  LucideAngularModule,
  ArrowRight,
  BadgePercent,
  CheckCircle2,
  CreditCard,
  Headphones,
  RotateCcw,
  ShieldCheck,
  ShoppingBag,
  Sparkles,
  Truck,
  Zap
} from 'lucide-angular';
import { Product } from '../../core/models/product.model';
import { CatalogService } from '../../services/catalog.service';
import { ProductCardComponent } from '../../shared/product-card/product-card.component';
import { SectionHeaderComponent } from '../../shared/section-header/section-header.component';
import { SkeletonProductCardComponent } from '../../shared/skeleton-product-card/skeleton-product-card.component';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-home-page',
  imports: [
    RouterLink,
    LucideAngularModule,
    ProductCardComponent,
    SectionHeaderComponent,
    SkeletonProductCardComponent,
    StatePanelComponent
  ],
  template: `
    <section class="relative overflow-hidden bg-aurora-radial dark:bg-aurora-dark-radial">
      <div class="page-shell grid min-h-[720px] items-center gap-10 py-10 lg:grid-cols-[0.96fr_1.04fr] lg:py-16">
        <div class="relative z-10 max-w-3xl animate-fadeUp">
          <div class="inline-flex items-center gap-2 rounded-ui border border-white/70 bg-white/80 px-3 py-2 text-xs font-black uppercase tracking-[0.16em] text-aurora-gold shadow-sm backdrop-blur dark:border-white/10 dark:bg-white/10 dark:text-amber-300">
            <lucide-icon [img]="Sparkles" size="14" />
            New season edit
          </div>
          <h1 class="mt-5 max-w-3xl text-5xl font-black leading-[0.95] tracking-normal text-aurora-ink sm:text-6xl lg:text-7xl dark:text-white">
            Curated commerce for people who notice the details.
          </h1>
          <p class="mt-6 max-w-2xl text-base leading-8 text-aurora-muted sm:text-lg dark:text-stone-300">
            Shop polished tech, audio, home and travel essentials in a calm storefront built for confident decisions.
          </p>

          <div class="mt-8 flex flex-col gap-3 sm:flex-row">
            <a routerLink="/catalog" class="ui-button ui-button-primary">
              Shop the edit
              <lucide-icon [img]="ArrowRight" size="18" />
            </a>
            <a routerLink="/register" class="ui-button ui-button-secondary">Create account</a>
          </div>

          <div class="mt-10 grid max-w-2xl gap-3 sm:grid-cols-3">
            @for (metric of heroMetrics; track metric.label) {
              <div class="surface-panel p-4">
                <p class="text-2xl font-black text-aurora-ink dark:text-white">{{ metric.value }}</p>
                <p class="mt-1 text-sm font-semibold text-aurora-muted dark:text-stone-300">{{ metric.label }}</p>
              </div>
            }
          </div>
        </div>

        <div class="relative min-h-[520px]">
          <div class="absolute inset-x-8 top-4 h-72 rounded-ui bg-aurora-amber/20 blur-3xl dark:bg-amber-400/10"></div>
          <div class="premium-shell relative overflow-hidden p-3">
            <img
              class="h-[560px] w-full rounded-ui object-cover"
              src="https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=1400&q=85"
              alt="Premium desk with laptop, headphones and curated commerce products"
            />
            <div class="absolute inset-3 rounded-ui bg-gradient-to-t from-aurora-night/55 via-transparent to-transparent"></div>

            <div class="absolute bottom-6 left-6 right-6 grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
              <div class="rounded-ui border border-white/30 bg-white/90 p-4 shadow-premium backdrop-blur-xl dark:bg-aurora-night/80">
                <p class="text-xs font-black uppercase tracking-[0.16em] text-aurora-gold dark:text-amber-300">Weekend capsule</p>
                <p class="mt-2 text-xl font-black text-aurora-ink dark:text-white">Work-ready devices and calm everyday carry.</p>
              </div>
              <div class="hidden rounded-ui border border-white/30 bg-aurora-ink p-4 text-white shadow-premium sm:block">
                <p class="text-3xl font-black">24h</p>
                <p class="mt-1 text-sm text-stone-300">priority dispatch</p>
              </div>
            </div>
          </div>

          <div class="absolute -right-2 top-10 hidden animate-float rounded-ui border border-white/70 bg-white/90 p-4 shadow-glow backdrop-blur-xl lg:block dark:border-white/10 dark:bg-white/10">
            <div class="flex items-center gap-3">
              <span class="flex h-10 w-10 items-center justify-center rounded-ui bg-emerald-50 text-aurora-emerald dark:bg-emerald-500/15">
                <lucide-icon [img]="CheckCircle2" size="20" />
              </span>
              <div>
                <p class="text-sm font-black text-aurora-ink dark:text-white">Stock-aware</p>
                <p class="text-xs text-aurora-muted dark:text-stone-300">Ready for real carts</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="page-shell py-14">
      <app-section-header eyebrow="Featured paths" title="Shop the moments" description="Visual entry points for focused browsing, from desk upgrades to travel-ready essentials.">
        <a routerLink="/catalog" class="ui-button ui-button-secondary">View all</a>
      </app-section-header>

      <div class="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        @for (category of categories; track category.name) {
          <a routerLink="/catalog" class="soft-card group cursor-pointer overflow-hidden">
            <div class="relative h-48 overflow-hidden">
              <img class="h-full w-full object-cover transition duration-500 group-hover:scale-[1.04] motion-reduce:transition-none motion-reduce:group-hover:scale-100" [src]="category.image" [alt]="category.name" />
              <div class="absolute inset-0 bg-gradient-to-t from-aurora-night/70 via-aurora-night/10 to-transparent"></div>
              <div class="absolute bottom-4 left-4 right-4">
                <p class="text-xl font-black text-white">{{ category.name }}</p>
                <p class="mt-1 text-sm font-medium text-stone-200">{{ category.copy }}</p>
              </div>
            </div>
          </a>
        }
      </div>
    </section>

    <section class="border-y border-aurora-line/80 bg-white/70 py-14 dark:border-white/10 dark:bg-white/[0.03]">
      <div class="page-shell">
        <app-section-header eyebrow="Featured products" title="Selected for this week" description="A polished product rail that already handles loading, empty and service-error states.">
          <a routerLink="/catalog" class="ui-button ui-button-primary">Open catalog</a>
        </app-section-header>

        @if (loading()) {
          <div class="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            @for (item of skeletonItems; track item) {
              <app-skeleton-product-card />
            }
          </div>
        } @else if (error()) {
          <div class="mt-8">
            <app-state-panel mode="error" title="Products are offline" [message]="error()!" />
          </div>
        } @else if (featuredProducts().length === 0) {
          <div class="mt-8">
            <app-state-panel title="The shelf is waiting" message="Create active products in the admin API and they will appear here." />
          </div>
        } @else {
          <div class="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            @for (product of featuredProducts(); track product.id) {
              <app-product-card [product]="product" />
            }
          </div>
        }
      </div>
    </section>

    <section class="page-shell py-14">
      <div class="grid gap-5 lg:grid-cols-[1.25fr_0.75fr]">
        <div class="relative overflow-hidden rounded-ui bg-aurora-night p-6 text-white shadow-premium sm:p-8">
          <img class="absolute inset-0 h-full w-full object-cover opacity-35" src="https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1400&q=80" alt="Curated travel and lifestyle products" />
          <div class="relative z-10 max-w-xl">
            <p class="section-kicker">Limited offer</p>
            <h2 class="mt-3 text-4xl font-black leading-tight">Build a sharper everyday kit.</h2>
            <p class="mt-4 text-sm leading-6 text-stone-200">Bundle desk, audio and travel picks with seasonal pricing and clean checkout flow.</p>
            <a routerLink="/catalog" class="ui-button mt-7 bg-white text-aurora-night hover:bg-stone-100">
              Browse offers
              <lucide-icon [img]="BadgePercent" size="18" />
            </a>
          </div>
        </div>

        <div class="grid gap-5">
          @for (benefit of benefits; track benefit.title) {
            <div class="surface-panel p-5">
              <div class="flex items-start gap-4">
                <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-ui bg-amber-50 text-aurora-gold dark:bg-amber-400/10 dark:text-amber-300">
                  <lucide-icon [img]="benefit.icon" size="21" />
                </span>
                <div>
                  <h3 class="font-black text-aurora-ink dark:text-white">{{ benefit.title }}</h3>
                  <p class="mt-1 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ benefit.copy }}</p>
                </div>
              </div>
            </div>
          }
        </div>
      </div>
    </section>

    <section class="page-shell py-14">
      <div class="grid gap-4 md:grid-cols-3">
        @for (promise of promises; track promise.title) {
          <div class="soft-card p-6">
            <lucide-icon class="text-aurora-ocean dark:text-blue-300" [img]="promise.icon" size="24" />
            <h3 class="mt-5 text-lg font-black text-aurora-ink dark:text-white">{{ promise.title }}</h3>
            <p class="mt-2 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ promise.copy }}</p>
          </div>
        }
      </div>
    </section>

    <section class="page-shell py-14">
      <div class="overflow-hidden rounded-ui border border-aurora-line bg-white shadow-premium dark:border-white/10 dark:bg-white/[0.07]">
        <div class="grid items-center gap-0 lg:grid-cols-[0.9fr_1.1fr]">
          <img class="h-72 w-full object-cover lg:h-full" src="https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=1400&q=85" alt="Premium product assortment" />
          <div class="p-7 sm:p-10">
            <p class="section-kicker">Aurora club</p>
            <h2 class="mt-3 text-3xl font-black text-aurora-ink sm:text-4xl dark:text-white">Get first access to the next drop.</h2>
            <p class="mt-4 max-w-xl text-sm leading-6 text-aurora-muted dark:text-stone-300">A premium account keeps favorites, carts and future order history ready across the marketplace.</p>
            <div class="mt-7 flex flex-col gap-3 sm:flex-row">
              <a routerLink="/register" class="ui-button ui-button-primary">
                Join Aurora
                <lucide-icon [img]="ArrowRight" size="18" />
              </a>
              <a routerLink="/login" class="ui-button ui-button-secondary">Sign in</a>
            </div>
          </div>
        </div>
      </div>
    </section>
  `
})
export class HomePageComponent implements OnInit {
  readonly featuredProducts = signal<Product[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly ArrowRight = ArrowRight;
  readonly BadgePercent = BadgePercent;
  readonly CheckCircle2 = CheckCircle2;
  readonly Sparkles = Sparkles;

  readonly skeletonItems = [1, 2, 3, 4];
  readonly heroMetrics = [
    { value: '4.8', label: 'average rating' },
    { value: '2k+', label: 'curated items' },
    { value: '24h', label: 'priority dispatch' }
  ];
  readonly categories = [
    { name: 'Work tech', copy: 'Laptops, displays and desk gear', image: 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=900&q=80' },
    { name: 'Audio', copy: 'Immersive listening and portable sound', image: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80' },
    { name: 'Home edit', copy: 'Connected essentials with quiet polish', image: 'https://images.unsplash.com/photo-1519710164239-da123dc03ef4?auto=format&fit=crop&w=900&q=80' },
    { name: 'Travel kit', copy: 'Carry smarter from gate to weekend', image: 'https://images.unsplash.com/photo-1553531384-cc64ac80f931?auto=format&fit=crop&w=900&q=80' }
  ];
  readonly benefits = [
    { icon: Truck, title: 'Fast shipping', copy: 'Delivery promises are visible and easy to scan before checkout.' },
    { icon: CreditCard, title: 'Secure payment', copy: 'Payment moments are designed to feel deliberate, focused and calm.' },
    { icon: Headphones, title: 'Helpful support', copy: 'Support entry points stay close to the shopping journey.' },
    { icon: RotateCcw, title: 'Easy returns', copy: 'Return confidence is presented before the final decision.' }
  ];
  readonly promises = [
    { icon: ShoppingBag, title: 'Premium product browsing', copy: 'Cards, galleries, filters and CTAs share one polished design language.' },
    { icon: ShieldCheck, title: 'Trust-first flows', copy: 'Visuals reinforce secure accounts, protected routes and backend-owned commerce truth.' },
    { icon: Zap, title: 'Ready to extend', copy: 'The shell is prepared for cart, wishlist, checkout and order screens without redesign.' }
  ];

  constructor(private readonly catalogService: CatalogService) {}

  ngOnInit(): void {
    this.catalogService.getProducts().subscribe({
      next: (products) => {
        this.featuredProducts.set(products.filter((product) => product.featured).slice(0, 4));
        if (this.featuredProducts().length === 0) {
          this.featuredProducts.set(products.slice(0, 4));
        }
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Start the backend on port 8080 to load products from /api/products.');
        this.loading.set(false);
      }
    });
  }
}
