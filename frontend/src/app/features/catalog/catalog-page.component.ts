import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LucideAngularModule, Grid3X3, Search, SlidersHorizontal, Sparkles, X } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
import { Brand, Category, Product } from '../../core/models/product.model';
import { CatalogService } from '../../services/catalog.service';
import { ProductCardComponent } from '../../shared/product-card/product-card.component';
import { SectionHeaderComponent } from '../../shared/section-header/section-header.component';
import { SkeletonProductCardComponent } from '../../shared/skeleton-product-card/skeleton-product-card.component';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-catalog-page',
  imports: [LucideAngularModule, TranslatePipe, ProductCardComponent, SectionHeaderComponent, SkeletonProductCardComponent, StatePanelComponent],
  template: `
    <section class="page-shell py-10 sm:py-12">
      <div class="premium-shell overflow-hidden">
        <div class="grid gap-0 lg:grid-cols-[0.85fr_1.15fr]">
          <div class="p-6 sm:p-8 lg:p-10">
            <p class="section-kicker">{{ 'nav.catalog' | t }}</p>
            <h1 class="mt-3 max-w-2xl text-4xl font-black leading-tight text-aurora-ink sm:text-5xl dark:text-white">{{ 'catalog.title' | t }}</h1>
            <p class="mt-4 max-w-xl text-sm leading-6 text-aurora-muted dark:text-stone-300">
              {{ 'catalog.subtitle' | t }}
            </p>
          </div>
          <div class="relative min-h-64 overflow-hidden bg-aurora-night">
            <img class="absolute inset-0 h-full w-full object-cover opacity-75" src="https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=1400&q=85" alt="Premium sneaker and product catalog display" />
            <div class="absolute inset-0 bg-gradient-to-l from-aurora-night/30 to-aurora-night/80"></div>
            <div class="absolute bottom-6 left-6 right-6 flex flex-wrap gap-3">
              <span class="aurora-badge border-white/20 bg-white/15 text-white">
                <lucide-icon [img]="Sparkles" size="14" />
                {{ 'catalog.newDrops' | t }}
              </span>
              <span class="aurora-badge border-white/20 bg-white/15 text-white">
                <lucide-icon [img]="Grid3X3" size="14" />
                {{ products().length }} {{ 'catalog.productsCount' | t }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="mt-8 flex flex-col gap-3 lg:hidden">
        <label class="field-shell">
          <lucide-icon class="text-stone-400" [img]="Search" size="17" />
          <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" [value]="query()" (input)="query.set($any($event.target).value)" [placeholder]="'catalog.searchPlaceholder' | t" />
        </label>
        <div class="grid grid-cols-2 gap-3">
          <button class="ui-button ui-button-primary" type="button" (click)="search()">{{ 'catalog.search' | t }}</button>
          <button class="ui-button ui-button-secondary" type="button" (click)="filtersOpen.set(true)">
            <lucide-icon [img]="SlidersHorizontal" size="17" />
            {{ 'catalog.filters' | t }}
          </button>
        </div>
      </div>

      <div class="mt-8 grid gap-6 lg:grid-cols-[300px_1fr]">
        <aside class="sticky top-28 hidden h-fit lg:block">
          <div class="surface-panel p-5">
            <div class="flex items-center justify-between gap-3">
              <div class="flex items-center gap-2 font-black text-aurora-ink dark:text-white">
                <lucide-icon [img]="SlidersHorizontal" size="18" />
                {{ 'catalog.refine' | t }}
              </div>
              <span class="text-xs font-bold text-aurora-muted dark:text-stone-400">{{ products().length }} {{ 'catalog.itemsCount' | t }}</span>
            </div>

            <label class="mt-5 block">
              <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'catalog.search' | t }}</span>
              <span class="field-shell">
                <lucide-icon class="text-stone-400" [img]="Search" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" [value]="query()" (input)="query.set($any($event.target).value)" [placeholder]="'catalog.searchHint' | t" />
              </span>
            </label>
            <button class="ui-button ui-button-primary mt-4 w-full" type="button" (click)="search()">{{ 'catalog.apply' | t }}</button>

            <div class="mt-6 border-t border-aurora-line pt-6 dark:border-white/10">
              <p class="text-sm font-black text-aurora-ink dark:text-white">{{ 'catalog.categories' | t }}</p>
              <div class="mt-3 flex flex-wrap gap-2">
                @for (category of categories(); track category.id) {
                  <button class="aurora-chip" type="button">{{ category.name }}</button>
                } @empty {
                  <span class="text-sm text-aurora-muted dark:text-stone-400">{{ 'catalog.noCategories' | t }}</span>
                }
              </div>
            </div>

            <div class="mt-6 border-t border-aurora-line pt-6 dark:border-white/10">
              <p class="text-sm font-black text-aurora-ink dark:text-white">{{ 'catalog.brands' | t }}</p>
              <div class="mt-3 flex flex-wrap gap-2">
                @for (brand of brands(); track brand.id) {
                  <button class="aurora-chip" type="button">{{ brand.name }}</button>
                } @empty {
                  <span class="text-sm text-aurora-muted dark:text-stone-400">{{ 'catalog.noBrands' | t }}</span>
                }
              </div>
            </div>
          </div>
        </aside>

        <div>
          <div class="mb-5 hidden items-center justify-between gap-4 lg:flex">
            <app-section-header eyebrow="{{ 'catalog.results' | t }}" title="{{ 'catalog.grid' | t }}" description="{{ 'catalog.gridDescription' | t }}" />
          </div>

          @if (loading()) {
            <div class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
              @for (item of skeletonItems; track item) {
                <app-skeleton-product-card />
              }
            </div>
          } @else if (error()) {
            <app-state-panel mode="error" title="{{ 'catalog.error' | t }}" [message]="error()!" />
          } @else if (products().length === 0) {
            <app-state-panel title="{{ 'catalog.empty' | t }}" message="{{ 'catalog.emptyMessage' | t }}" />
          } @else {
            <div class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
              @for (product of products(); track product.id) {
                <app-product-card [product]="product" />
              }
            </div>
          }
        </div>
      </div>
    </section>

    @if (filtersOpen()) {
      <div class="fixed inset-0 z-50 bg-aurora-night/55 p-3 backdrop-blur-sm lg:hidden" (click)="filtersOpen.set(false)">
        <div class="absolute inset-x-3 bottom-3 rounded-ui border border-white/70 bg-white p-5 shadow-premium dark:border-white/10 dark:bg-aurora-night" (click)="$event.stopPropagation()">
          <div class="flex items-center justify-between gap-3">
            <div class="flex items-center gap-2 font-black text-aurora-ink dark:text-white">
              <lucide-icon [img]="SlidersHorizontal" size="18" />
              {{ 'catalog.filters' | t }}
            </div>
            <button class="ui-button ui-button-secondary h-10 w-10 min-h-10 p-0" type="button" (click)="filtersOpen.set(false)" [attr.aria-label]="'a11y.closeFilters' | t">
              <lucide-icon [img]="X" size="18" />
            </button>
          </div>

          <div class="mt-5">
            <p class="text-sm font-black text-aurora-ink dark:text-white">{{ 'catalog.categories' | t }}</p>
            <div class="mt-3 flex flex-wrap gap-2">
              @for (category of categories(); track category.id) {
                <button class="aurora-chip" type="button">{{ category.name }}</button>
              } @empty {
                <span class="text-sm text-aurora-muted dark:text-stone-400">{{ 'catalog.noCategories' | t }}</span>
              }
            </div>
          </div>

          <div class="mt-5">
            <p class="text-sm font-black text-aurora-ink dark:text-white">{{ 'catalog.brands' | t }}</p>
            <div class="mt-3 flex flex-wrap gap-2">
              @for (brand of brands(); track brand.id) {
                <button class="aurora-chip" type="button">{{ brand.name }}</button>
              } @empty {
                <span class="text-sm text-aurora-muted dark:text-stone-400">{{ 'catalog.noBrands' | t }}</span>
              }
            </div>
          </div>

          <button class="ui-button ui-button-primary mt-6 w-full" type="button" (click)="filtersOpen.set(false)">{{ 'catalog.filters' | t }}</button>
        </div>
      </div>
    }
  `
})
export class CatalogPageComponent implements OnInit {
  readonly products = signal<Product[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly brands = signal<Brand[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly query = signal('');
  readonly filtersOpen = signal(false);

  readonly Grid3X3 = Grid3X3;
  readonly Search = Search;
  readonly SlidersHorizontal = SlidersHorizontal;
  readonly Sparkles = Sparkles;
  readonly X = X;
  readonly skeletonItems = [1, 2, 3, 4, 5, 6];

  constructor(
    private readonly catalogService: CatalogService,
    private readonly language: LanguageService,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    // Filters load once; product errors are surfaced by the results grid.
    this.catalogService.getCategories().subscribe({ next: (c) => this.categories.set(c), error: () => undefined });
    this.catalogService.getBrands().subscribe({ next: (b) => this.brands.set(b), error: () => undefined });

    // React to the global search box (?q=...) as well as in-page searches.
    this.route.queryParamMap.subscribe((params) => {
      this.query.set(params.get('q') ?? '');
      this.loadProducts();
    });
  }

  search(): void {
    this.loadProducts();
  }

  private loadProducts(): void {
    const value = this.query().trim();
    this.loading.set(true);
    this.error.set(null);

    const request = value ? this.catalogService.searchProducts(value) : this.catalogService.getProducts();
    request.subscribe({
      next: (products) => {
        this.products.set(products);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.language.translate(value ? 'catalog.searchError' : 'catalog.loadError'));
        this.loading.set(false);
      }
    });
  }
}
