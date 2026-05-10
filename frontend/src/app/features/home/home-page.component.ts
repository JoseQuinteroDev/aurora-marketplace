import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowRight, CheckCircle2, CreditCard, ShieldCheck, Sparkles, Truck } from 'lucide-angular';
import { Product } from '../../core/models/product.model';
import { CatalogService } from '../../services/catalog.service';
import { ProductCardComponent } from '../../shared/product-card/product-card.component';
import { SectionHeaderComponent } from '../../shared/section-header/section-header.component';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, LucideAngularModule, ProductCardComponent, SectionHeaderComponent, StatePanelComponent],
  template: `
    <section class="relative overflow-hidden">
      <div class="page-shell grid min-h-[680px] items-center gap-10 py-10 lg:grid-cols-[1.02fr_0.98fr] lg:py-16">
        <div class="relative z-10 max-w-3xl">
          <p class="section-kicker">Aurora commerce cloud</p>
          <h1 class="mt-4 text-5xl font-black tracking-normal text-slate-950 sm:text-6xl lg:text-7xl dark:text-white">
            Discover products with checkout-ready confidence.
          </h1>
          <p class="mt-6 max-w-2xl text-base leading-8 text-slate-600 sm:text-lg dark:text-slate-300">
            A premium marketplace interface backed by secure auth, inventory, coupons, simulated payments and admin operations.
          </p>
          <div class="mt-8 flex flex-col gap-3 sm:flex-row">
            <a routerLink="/catalog" class="ui-button ui-button-primary">
              Shop the catalog
              <lucide-icon [img]="ArrowRight" size="18" />
            </a>
            <a routerLink="/register" class="ui-button ui-button-secondary">Create account</a>
          </div>
          <div class="mt-10 grid gap-3 sm:grid-cols-3">
            @for (benefit of heroBenefits; track benefit.label) {
              <div class="rounded-ui border border-slate-200 bg-white/70 p-4 text-sm shadow-sm backdrop-blur dark:border-white/10 dark:bg-white/[0.08]">
                <lucide-icon class="text-aurora-emerald" [img]="benefit.icon" size="20" />
                <p class="mt-3 font-bold text-slate-950 dark:text-white">{{ benefit.label }}</p>
                <p class="mt-1 text-slate-600 dark:text-slate-300">{{ benefit.copy }}</p>
              </div>
            }
          </div>
        </div>

        <div class="relative">
          <div class="glass-panel overflow-hidden rounded-ui">
            <img
              class="h-[520px] w-full object-cover"
              src="https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=1200&q=85"
              alt="Premium laptop product photography"
            />
          </div>
          <div class="absolute -bottom-6 left-6 right-6 rounded-ui border border-white/70 bg-white/90 p-4 shadow-premium backdrop-blur-xl dark:border-white/10 dark:bg-slate-950/90">
            <div class="flex items-center justify-between gap-4">
              <div>
                <p class="text-xs font-bold uppercase tracking-[0.18em] text-aurora-ocean">Live backend ready</p>
                <p class="mt-1 font-bold text-slate-950 dark:text-white">Catalog, cart, checkout and admin APIs</p>
              </div>
              <div class="hidden h-12 w-12 items-center justify-center rounded-ui bg-emerald-500 text-white sm:flex">
                <lucide-icon [img]="CheckCircle2" size="22" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="page-shell py-12">
      <app-section-header eyebrow="Curated paths" title="Shop by category" description="Fast visual entry points for the kinds of marketplace journeys Aurora will support.">
        <a routerLink="/catalog" class="ui-button ui-button-secondary">View catalog</a>
      </app-section-header>
      <div class="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        @for (category of categories; track category.name) {
          <a routerLink="/catalog" class="soft-card group cursor-pointer overflow-hidden">
            <img class="h-40 w-full object-cover transition-transform duration-300 group-hover:scale-[1.03] motion-reduce:transition-none motion-reduce:group-hover:scale-100" [src]="category.image" [alt]="category.name" />
            <div class="p-4">
              <p class="font-bold text-slate-950 dark:text-white">{{ category.name }}</p>
              <p class="mt-1 text-sm text-slate-600 dark:text-slate-300">{{ category.copy }}</p>
            </div>
          </a>
        }
      </div>
    </section>

    <section class="bg-white/70 py-12 dark:bg-white/[0.03]">
      <div class="page-shell">
        <app-section-header eyebrow="Featured" title="API-ready product rails" description="The storefront is wired to the backend product endpoint and already handles loading, empty and error states." />

        @if (loading()) {
          <div class="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            @for (item of skeletonItems; track item) {
              <div class="soft-card h-[392px] animate-pulse overflow-hidden">
                <div class="h-52 bg-slate-200 dark:bg-white/10"></div>
                <div class="space-y-3 p-4">
                  <div class="h-3 w-24 rounded bg-slate-200 dark:bg-white/10"></div>
                  <div class="h-5 w-full rounded bg-slate-200 dark:bg-white/10"></div>
                  <div class="h-4 w-4/5 rounded bg-slate-200 dark:bg-white/10"></div>
                  <div class="h-10 w-full rounded bg-slate-200 dark:bg-white/10"></div>
                </div>
              </div>
            }
          </div>
        } @else if (error()) {
          <div class="mt-8">
            <app-state-panel mode="error" title="Products are offline" [message]="error()!" />
          </div>
        } @else if (featuredProducts().length === 0) {
          <div class="mt-8">
            <app-state-panel title="No featured products yet" message="Create products from the admin API and they will appear here." />
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

    <section class="page-shell grid gap-6 py-12 lg:grid-cols-3">
      @for (promise of promises; track promise.title) {
        <div class="rounded-ui border border-slate-200 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-white/[0.06]">
          <lucide-icon class="text-aurora-ocean" [img]="promise.icon" size="24" />
          <h3 class="mt-5 text-lg font-bold text-slate-950 dark:text-white">{{ promise.title }}</h3>
          <p class="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ promise.copy }}</p>
        </div>
      }
    </section>

    <section class="page-shell py-12">
      <div class="rounded-ui bg-slate-950 p-8 text-white shadow-premium md:p-12">
        <div class="grid items-center gap-8 md:grid-cols-[1.3fr_0.7fr]">
          <div>
            <p class="text-xs font-bold uppercase tracking-[0.18em] text-emerald-300">Launch offer</p>
            <h2 class="mt-3 text-3xl font-black sm:text-4xl">Build, browse and checkout in one polished flow.</h2>
            <p class="mt-4 max-w-2xl text-sm leading-6 text-slate-300">Aurora is ready for frontend iteration across catalog, cart, orders and admin tools.</p>
          </div>
          <a routerLink="/catalog" class="ui-button bg-white text-slate-950 hover:bg-slate-100">Explore Aurora</a>
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
  readonly CheckCircle2 = CheckCircle2;

  readonly skeletonItems = [1, 2, 3, 4];
  readonly heroBenefits = [
    { icon: ShieldCheck, label: 'Secure account flows', copy: 'JWT auth and protected APIs.' },
    { icon: CreditCard, label: 'Checkout ready', copy: 'Cart, coupons and simulated pay.' },
    { icon: Truck, label: 'Stock aware', copy: 'Inventory stays backend-owned.' }
  ];
  readonly categories = [
    { name: 'Work tech', copy: 'Laptops, displays and desk gear', image: 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=800&q=80' },
    { name: 'Audio', copy: 'Headphones and portable sound', image: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=80' },
    { name: 'Home essentials', copy: 'Premium connected living', image: 'https://images.unsplash.com/photo-1519710164239-da123dc03ef4?auto=format&fit=crop&w=800&q=80' },
    { name: 'Travel gear', copy: 'Everyday carry and mobility', image: 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=800&q=80' }
  ];
  readonly promises = [
    { icon: Sparkles, title: 'Premium by default', copy: 'Reusable sections, clean hierarchy, polished hover and focus states.' },
    { icon: ShieldCheck, title: 'Backend-trusted commerce', copy: 'Prices, ownership, stock and authorization stay server-side.' },
    { icon: CreditCard, title: 'Ready for checkout', copy: 'Order creation and simulated payments are already represented in the flow.' }
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
