import { Component, OnInit, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, Search, SlidersHorizontal } from 'lucide-angular';
import { Brand, Category, Product } from '../../core/models/product.model';
import { CatalogService } from '../../services/catalog.service';
import { ProductCardComponent } from '../../shared/product-card/product-card.component';
import { SectionHeaderComponent } from '../../shared/section-header/section-header.component';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-catalog-page',
  imports: [LucideAngularModule, ProductCardComponent, SectionHeaderComponent, StatePanelComponent],
  template: `
    <section class="page-shell py-10">
      <app-section-header eyebrow="Catalog" title="Browse Aurora products" description="A premium product grid prepared for filters, search and backend-driven merchandising." />

      <div class="mt-8 grid gap-6 lg:grid-cols-[280px_1fr]">
        <aside class="h-fit rounded-ui border border-slate-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-white/[0.06]">
          <div class="flex items-center gap-2 font-bold text-slate-950 dark:text-white">
            <lucide-icon [img]="SlidersHorizontal" size="18" />
            Filters
          </div>
          <label class="mt-5 block">
            <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">Search</span>
            <div class="mt-2 flex items-center gap-2 rounded-ui border border-slate-200 bg-white px-3 dark:border-white/10 dark:bg-white/10">
              <lucide-icon class="text-slate-400" [img]="Search" size="17" />
              <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" [value]="query()" (input)="query.set($any($event.target).value)" placeholder="MacBook, audio, travel" />
            </div>
          </label>
          <button class="ui-button ui-button-primary mt-4 w-full" type="button" (click)="search()">Apply search</button>

          <div class="mt-6">
            <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">Categories</p>
            <div class="mt-3 flex flex-wrap gap-2">
              @for (category of categories(); track category.id) {
                <span class="rounded-ui border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 dark:border-white/10 dark:text-slate-300">{{ category.name }}</span>
              } @empty {
                <span class="text-sm text-slate-500">No categories loaded</span>
              }
            </div>
          </div>

          <div class="mt-6">
            <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">Brands</p>
            <div class="mt-3 flex flex-wrap gap-2">
              @for (brand of brands(); track brand.id) {
                <span class="rounded-ui border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 dark:border-white/10 dark:text-slate-300">{{ brand.name }}</span>
              } @empty {
                <span class="text-sm text-slate-500">No brands loaded</span>
              }
            </div>
          </div>
        </aside>

        <div>
          @if (loading()) {
            <div class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
              @for (item of [1, 2, 3, 4, 5, 6]; track item) {
                <div class="soft-card h-[392px] animate-pulse overflow-hidden">
                  <div class="h-52 bg-slate-200 dark:bg-white/10"></div>
                  <div class="space-y-3 p-4">
                    <div class="h-3 w-24 rounded bg-slate-200 dark:bg-white/10"></div>
                    <div class="h-5 w-full rounded bg-slate-200 dark:bg-white/10"></div>
                    <div class="h-4 w-4/5 rounded bg-slate-200 dark:bg-white/10"></div>
                  </div>
                </div>
              }
            </div>
          } @else if (error()) {
            <app-state-panel mode="error" title="Catalog unavailable" [message]="error()!" />
          } @else if (products().length === 0) {
            <app-state-panel title="No products found" message="Try another search or create products from the admin API." />
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
  `
})
export class CatalogPageComponent implements OnInit {
  readonly products = signal<Product[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly brands = signal<Brand[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly query = signal('');

  readonly Search = Search;
  readonly SlidersHorizontal = SlidersHorizontal;

  constructor(private readonly catalogService: CatalogService) {}

  ngOnInit(): void {
    forkJoin({
      products: this.catalogService.getProducts(),
      categories: this.catalogService.getCategories(),
      brands: this.catalogService.getBrands()
    }).subscribe({
      next: ({ products, categories, brands }) => {
        this.products.set(products);
        this.categories.set(categories);
        this.brands.set(brands);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Start the backend on port 8080 to load the catalog.');
        this.loading.set(false);
      }
    });
  }

  search(): void {
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
        this.error.set('Search failed. Check that the backend is running.');
        this.loading.set(false);
      }
    });
  }
}
