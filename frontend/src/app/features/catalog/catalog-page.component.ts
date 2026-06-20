import { Component, OnInit, computed, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, Grid3X3, Search, SlidersHorizontal, Sparkles, X } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
import { Brand, Category, Product } from '../../core/models/product.model';
import { CatalogService } from '../../services/catalog.service';
import { ProductCardComponent } from '../../shared/product-card/product-card.component';
import { SectionHeaderComponent } from '../../shared/section-header/section-header.component';
import { SkeletonProductCardComponent } from '../../shared/skeleton-product-card/skeleton-product-card.component';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

type SortKey = 'featured' | 'price-asc' | 'price-desc' | 'name';

@Component({
  selector: 'app-catalog-page',
  imports: [LucideAngularModule, TranslatePipe, ProductCardComponent, SectionHeaderComponent, SkeletonProductCardComponent, StatePanelComponent],
  template: `
    <section class="page-shell py-10 sm:py-12">
      <div class="premium-shell overflow-hidden">
        <div class="grid gap-0 lg:grid-cols-[0.85fr_1.15fr]">
          <div class="p-6 sm:p-8 lg:p-10">
            <p class="section-kicker">{{ 'nav.catalog' | t }}</p>
            <h1 class="mt-3 max-w-2xl text-4xl font-semibold leading-tight text-aurora-ink sm:text-5xl dark:text-white">{{ 'catalog.title' | t }}</h1>
            <p class="mt-4 max-w-xl text-sm leading-6 text-aurora-muted dark:text-stone-300">
              {{ 'catalog.subtitle' | t }}
            </p>
          </div>
          <div class="relative min-h-64 overflow-hidden bg-aurora-night">
            <img class="absolute inset-0 h-full w-full object-cover opacity-75" src="https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=1400&q=85" alt="Catálogo de productos de Aurora" />
            <div class="absolute inset-0 bg-gradient-to-l from-aurora-night/30 to-aurora-night/80"></div>
            <div class="absolute bottom-6 left-6 right-6 flex flex-wrap gap-3">
              <span class="aurora-badge border-white/20 bg-white/15 text-white">
                <lucide-icon [img]="Sparkles" size="14" />
                {{ 'catalog.newDrops' | t }}
              </span>
              <span class="aurora-badge border-white/20 bg-white/15 text-white">
                <lucide-icon [img]="Grid3X3" size="14" />
                {{ visibleProducts().length }} {{ 'catalog.productsCount' | t }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="mt-8 flex flex-col gap-3 lg:hidden">
        <label class="field-shell">
          <lucide-icon class="text-stone-400" [img]="Search" size="17" />
          <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" [value]="query()" (input)="query.set($any($event.target).value)" (keyup.enter)="search()" [placeholder]="'catalog.searchPlaceholder' | t" [attr.aria-label]="'catalog.searchPlaceholder' | t" />
        </label>
        <div class="grid grid-cols-2 gap-3">
          <button class="ui-button ui-button-primary" type="button" (click)="search()">{{ 'catalog.search' | t }}</button>
          <button class="ui-button ui-button-secondary" type="button" (click)="openFilters()">
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
              @if (selectedCategory() || selectedBrand()) {
                <button class="cursor-pointer text-xs font-bold text-aurora-gold underline dark:text-aurora-pinebright" type="button" (click)="clearFilters()">{{ 'catalog.clearFilters' | t }}</button>
              } @else {
                <span class="text-xs font-bold text-aurora-muted dark:text-stone-400">{{ visibleProducts().length }} {{ 'catalog.itemsCount' | t }}</span>
              }
            </div>

            <label class="mt-5 block">
              <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'catalog.search' | t }}</span>
              <span class="field-shell">
                <lucide-icon class="text-stone-400" [img]="Search" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" [value]="query()" (input)="query.set($any($event.target).value)" (keyup.enter)="search()" [placeholder]="'catalog.searchHint' | t" />
              </span>
            </label>
            <button class="ui-button ui-button-primary mt-4 w-full" type="button" (click)="search()">{{ 'catalog.apply' | t }}</button>

            <div class="mt-6 border-t border-aurora-line pt-6 dark:border-white/10">
              <p class="text-sm font-black text-aurora-ink dark:text-white">{{ 'catalog.categories' | t }}</p>
              <div class="mt-3 flex flex-wrap gap-2">
                @for (category of categories(); track category.id) {
                  <button class="aurora-chip" type="button" [class.ring-2]="selectedCategory() === category.slug" [class.ring-aurora-amber]="selectedCategory() === category.slug" [class.ring-offset-1]="selectedCategory() === category.slug" (click)="toggleCategory(category.slug)">{{ category.name }}</button>
                } @empty {
                  <span class="text-sm text-aurora-muted dark:text-stone-400">{{ 'catalog.noCategories' | t }}</span>
                }
              </div>
            </div>

            <div class="mt-6 border-t border-aurora-line pt-6 dark:border-white/10">
              <p class="text-sm font-black text-aurora-ink dark:text-white">{{ 'catalog.brands' | t }}</p>
              <div class="mt-3 flex flex-wrap gap-2">
                @for (brand of brands(); track brand.id) {
                  <button class="aurora-chip" type="button" [class.ring-2]="selectedBrand() === brand.slug" [class.ring-aurora-amber]="selectedBrand() === brand.slug" [class.ring-offset-1]="selectedBrand() === brand.slug" (click)="toggleBrand(brand.slug)">{{ brand.name }}</button>
                } @empty {
                  <span class="text-sm text-aurora-muted dark:text-stone-400">{{ 'catalog.noBrands' | t }}</span>
                }
              </div>
            </div>
          </div>
        </aside>

        <div>
          <div class="mb-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <app-section-header eyebrow="{{ 'catalog.results' | t }}" title="{{ 'catalog.grid' | t }}" description="{{ 'catalog.gridDescription' | t }}" />
            <label class="flex items-center gap-2 self-start sm:self-auto">
              <span class="text-xs font-bold uppercase tracking-[0.12em] text-aurora-muted dark:text-stone-400">{{ 'catalog.sortBy' | t }}</span>
              <select class="ui-input h-11 cursor-pointer py-0 pr-8 text-sm font-semibold" [value]="sortBy()" (change)="setSort($any($event.target).value)">
                @for (option of sortOptions; track option.value) {
                  <option [value]="option.value">{{ option.label | t }}</option>
                }
              </select>
            </label>
          </div>

          @if (loading()) {
            <div class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
              @for (item of skeletonItems; track item) {
                <app-skeleton-product-card />
              }
            </div>
          } @else if (error()) {
            <app-state-panel mode="error" title="{{ 'catalog.error' | t }}" [message]="error()!" />
          } @else if (visibleProducts().length === 0) {
            <app-state-panel title="{{ 'catalog.empty' | t }}" message="{{ 'catalog.emptyMessage' | t }}" />
            <div class="mt-6 text-center">
              <button class="ui-button ui-button-primary" type="button" (click)="resetAll()">{{ 'catalog.clearFilters' | t }}</button>
            </div>
          } @else {
            <div class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
              @for (product of visibleProducts(); track product.id) {
                <app-product-card [product]="product" />
              }
            </div>
          }
        </div>
      </div>
    </section>

    @if (filtersOpen()) {
      <div class="fixed inset-0 z-50 bg-aurora-night/55 p-3 backdrop-blur-sm lg:hidden" (click)="closeFilters()" (keydown)="onFilterKeydown($event)">
        <div class="absolute inset-x-3 bottom-3 max-h-[85vh] overflow-y-auto rounded-ui border border-white/70 bg-white p-5 shadow-premium dark:border-white/10 dark:bg-aurora-night" (click)="$event.stopPropagation()" role="dialog" aria-modal="true" data-filter-dialog [attr.aria-label]="'catalog.filters' | t">
          <div class="flex items-center justify-between gap-3">
            <div class="flex items-center gap-2 font-black text-aurora-ink dark:text-white">
              <lucide-icon [img]="SlidersHorizontal" size="18" />
              {{ 'catalog.filters' | t }}
            </div>
            <button class="ui-button ui-button-secondary h-10 w-10 min-h-10 p-0" type="button" (click)="closeFilters()" [attr.aria-label]="'a11y.closeFilters' | t">
              <lucide-icon [img]="X" size="18" />
            </button>
          </div>

          <div class="mt-5">
            <p class="text-sm font-black text-aurora-ink dark:text-white">{{ 'catalog.categories' | t }}</p>
            <div class="mt-3 flex flex-wrap gap-2">
              @for (category of categories(); track category.id) {
                <button class="aurora-chip" type="button" [class.ring-2]="selectedCategory() === category.slug" [class.ring-aurora-amber]="selectedCategory() === category.slug" [class.ring-offset-1]="selectedCategory() === category.slug" (click)="toggleCategory(category.slug)">{{ category.name }}</button>
              } @empty {
                <span class="text-sm text-aurora-muted dark:text-stone-400">{{ 'catalog.noCategories' | t }}</span>
              }
            </div>
          </div>

          <div class="mt-5">
            <p class="text-sm font-black text-aurora-ink dark:text-white">{{ 'catalog.brands' | t }}</p>
            <div class="mt-3 flex flex-wrap gap-2">
              @for (brand of brands(); track brand.id) {
                <button class="aurora-chip" type="button" [class.ring-2]="selectedBrand() === brand.slug" [class.ring-aurora-amber]="selectedBrand() === brand.slug" [class.ring-offset-1]="selectedBrand() === brand.slug" (click)="toggleBrand(brand.slug)">{{ brand.name }}</button>
              } @empty {
                <span class="text-sm text-aurora-muted dark:text-stone-400">{{ 'catalog.noBrands' | t }}</span>
              }
            </div>
          </div>

          <div class="mt-6 grid grid-cols-2 gap-2">
            <button class="ui-button ui-button-secondary" type="button" (click)="clearFilters()">{{ 'catalog.clearFilters' | t }}</button>
            <button class="ui-button ui-button-primary" type="button" (click)="closeFilters()">{{ 'catalog.apply' | t }}</button>
          </div>
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
  readonly selectedCategory = signal<string | null>(null);
  readonly selectedBrand = signal<string | null>(null);
  readonly sortBy = signal<SortKey>('featured');

  /** Client-side facet filtering + sorting over the loaded product list. */
  readonly visibleProducts = computed(() => {
    const category = this.selectedCategory();
    const brand = this.selectedBrand();
    const filtered = this.products().filter(
      (product) => (!category || product.category.slug === category) && (!brand || product.brand.slug === brand)
    );
    return this.sortProducts(filtered, this.sortBy());
  });

  readonly Grid3X3 = Grid3X3;
  readonly Search = Search;
  readonly SlidersHorizontal = SlidersHorizontal;
  readonly Sparkles = Sparkles;
  readonly X = X;
  readonly skeletonItems = [1, 2, 3, 4, 5, 6];
  readonly sortOptions: { value: SortKey; label: string }[] = [
    { value: 'featured', label: 'catalog.sort.featured' },
    { value: 'price-asc', label: 'catalog.sort.priceAsc' },
    { value: 'price-desc', label: 'catalog.sort.priceDesc' },
    { value: 'name', label: 'catalog.sort.name' }
  ];

  private loadedQuery: string | null = null;
  private filterReturnFocus: HTMLElement | null = null;

  constructor(
    private readonly catalogService: CatalogService,
    private readonly language: LanguageService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    // Filters load once; product errors are surfaced by the results grid.
    this.catalogService.getCategories().subscribe({ next: (c) => this.categories.set(c), error: () => undefined });
    this.catalogService.getBrands().subscribe({ next: (b) => this.brands.set(b), error: () => undefined });

    // The URL is the source of truth for search + facets, so global search,
    // home category deep-links and in-page filters all stay in sync.
    this.route.queryParamMap.subscribe((params) => {
      const query = params.get('q') ?? '';
      this.query.set(query);
      this.selectedCategory.set(params.get('category'));
      this.selectedBrand.set(params.get('brand'));
      const sort = params.get('sort');
      this.sortBy.set(this.isSortKey(sort) ? sort : 'featured');

      // Only the text query hits the backend; facets and sort are client-side.
      if (query !== this.loadedQuery) {
        this.loadProducts(query);
      }
    });
  }

  search(): void {
    this.updateParams({ q: this.query().trim() || null });
  }

  toggleCategory(slug: string): void {
    this.updateParams({ category: this.selectedCategory() === slug ? null : slug });
  }

  toggleBrand(slug: string): void {
    this.updateParams({ brand: this.selectedBrand() === slug ? null : slug });
  }

  setSort(value: string): void {
    this.updateParams({ sort: value === 'featured' ? null : value });
  }

  clearFilters(): void {
    this.updateParams({ category: null, brand: null });
    this.filtersOpen.set(false);
  }

  /** Clears the text query as well as the facets — the one-click reset the
   *  no-results empty state offers. */
  resetAll(): void {
    this.query.set('');
    this.updateParams({ q: null, category: null, brand: null });
    this.filtersOpen.set(false);
  }

  // --- Mobile filter dialog: focus management (no CDK) ---
  openFilters(): void {
    if (typeof document !== 'undefined') {
      this.filterReturnFocus = document.activeElement as HTMLElement | null;
    }
    this.filtersOpen.set(true);
    // Wait for the dialog to render, then move focus inside it.
    setTimeout(() => this.panelFocusables()[0]?.focus(), 0);
  }

  closeFilters(): void {
    this.filtersOpen.set(false);
    this.filterReturnFocus?.focus?.();
    this.filterReturnFocus = null;
  }

  onFilterKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeFilters();
      return;
    }
    if (event.key !== 'Tab') {
      return;
    }
    const focusables = this.panelFocusables();
    if (focusables.length === 0) {
      return;
    }
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && active === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  private panelFocusables(): HTMLElement[] {
    if (typeof document === 'undefined') {
      return [];
    }
    const panel = document.querySelector('[data-filter-dialog]');
    if (!panel) {
      return [];
    }
    return Array.from(
      panel.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
    );
  }

  private updateParams(patch: Record<string, string | null>): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: patch,
      queryParamsHandling: 'merge'
    });
  }

  private loadProducts(query: string): void {
    const value = query.trim();
    this.loadedQuery = query;
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

  private sortProducts(products: Product[], sort: SortKey): Product[] {
    const sorted = [...products];

    switch (sort) {
      case 'price-asc':
        return sorted.sort((a, b) => a.basePrice - b.basePrice);
      case 'price-desc':
        return sorted.sort((a, b) => b.basePrice - a.basePrice);
      case 'name':
        return sorted.sort((a, b) => a.name.localeCompare(b.name));
      default:
        return sorted.sort((a, b) => Number(b.featured) - Number(a.featured));
    }
  }

  private isSortKey(value: string | null): value is SortKey {
    return value === 'featured' || value === 'price-asc' || value === 'price-desc' || value === 'name';
  }
}
