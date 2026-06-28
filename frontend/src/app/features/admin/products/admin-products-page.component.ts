import { CurrencyPipe, NgClass } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { LucideAngularModule, CircleOff, Layers, Pencil, Plus, Power, Search, Star } from 'lucide-angular';
import { LanguageService } from '../../../core/i18n/language.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { AdminProductRequest, AdminProductResponse } from '../../../core/models/admin-product.model';
import { AdminProductService } from '../../../services/admin-product.service';
import { ToastService } from '../../../services/toast.service';
import { StatePanelComponent } from '../../../shared/state-panel/state-panel.component';

// The app registers no custom LOCALE_ID, so Angular only ships en-US locale data.
// The CurrencyPipe is instantiated with it to mirror the template `| currency`.
const DEFAULT_LOCALE = 'en-US';

@Component({
  selector: 'app-admin-products-page',
  imports: [CurrencyPipe, NgClass, ReactiveFormsModule, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="px-4 py-8 sm:px-6 lg:px-8">
      <div class="max-w-7xl">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p class="text-xs font-bold uppercase tracking-[0.18em] text-aurora-pinebright">{{ 'admin.products.eyebrow' | t }}</p>
            <h1 class="mt-3 text-3xl font-extrabold tracking-normal text-white sm:text-4xl">{{ 'admin.products.title' | t }}</h1>
            <p class="mt-3 max-w-2xl text-sm leading-6 text-aurora-mist/70">{{ 'admin.products.subtitle' | t }}</p>
          </div>
          <button class="ui-button ui-button-primary" type="button" (click)="create()">
            <lucide-icon [img]="Plus" size="18" />
            {{ 'admin.products.new' | t }}
          </button>
        </div>

        @if (!loading() && !error() && products().length > 0) {
          <div class="mt-8 max-w-md">
            <label class="relative block">
              <span class="sr-only">{{ 'admin.products.searchLabel' | t }}</span>
              <lucide-icon class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-aurora-mist/60" [img]="Search" size="18" />
              <input
                class="ui-input border-white/10 bg-white/[0.05] pl-10 text-white"
                type="search"
                [formControl]="search"
                [placeholder]="'admin.products.searchPlaceholder' | t"
              />
            </label>
          </div>
        }

        @if (loading()) {
          <div class="mt-8 grid gap-3">
            @for (item of [1, 2, 3, 4, 5]; track item) {
              <div class="h-16 animate-pulse rounded-ui bg-white/10"></div>
            }
          </div>
        } @else if (error()) {
          <div class="mt-8">
            <app-state-panel mode="error" title="{{ 'admin.products.errorTitle' | t }}" [message]="error()!" />
            <button class="ui-button mt-6 border border-white/10 bg-white/10 text-white hover:bg-white/15" type="button" (click)="load()">
              {{ 'common.retry' | t }}
            </button>
          </div>
        } @else if (products().length === 0) {
          <div class="mt-8">
            <app-state-panel title="{{ 'admin.products.emptyTitle' | t }}" message="{{ 'admin.products.empty' | t }}" />
          </div>
        } @else if (filtered().length === 0) {
          <div class="mt-8">
            <app-state-panel title="{{ 'admin.products.noMatchTitle' | t }}" message="{{ 'admin.products.noMatch' | t }}" />
          </div>
        } @else {
          <div class="mt-8 hidden overflow-x-auto rounded-ui border border-white/10 lg:block">
            <table class="w-full text-left text-sm">
              <caption class="sr-only">{{ 'admin.products.title' | t }}</caption>
              <thead class="bg-white/[0.06] text-xs uppercase tracking-[0.12em] text-aurora-mist/70">
                <tr>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.products.col.name' | t }}</th>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.products.col.category' | t }}</th>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.products.col.brand' | t }}</th>
                  <th scope="col" class="px-4 py-3 text-right font-semibold">{{ 'admin.products.col.price' | t }}</th>
                  <th scope="col" class="px-4 py-3 text-right font-semibold">{{ 'admin.products.col.variants' | t }}</th>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.products.col.state' | t }}</th>
                  <th scope="col" class="px-4 py-3 text-right font-semibold">{{ 'admin.products.col.actions' | t }}</th>
                </tr>
              </thead>
              <tbody>
                @for (product of filtered(); track product.id) {
                  <tr class="border-t border-white/10">
                    <td class="px-4 py-3">
                      <div class="flex items-center gap-2 font-extrabold text-white">
                        {{ product.name }}
                        @if (product.featured) {
                          <lucide-icon class="text-aurora-pinebright" [img]="Star" size="15" [attr.aria-label]="'admin.products.featured' | t" />
                        }
                      </div>
                    </td>
                    <td class="px-4 py-3 text-aurora-mist/80">{{ product.category.name }}</td>
                    <td class="px-4 py-3 text-aurora-mist/80">{{ product.brand.name }}</td>
                    <td class="px-4 py-3 text-right font-bold text-white">{{ formatPrice(product.basePrice) }}</td>
                    <td class="px-4 py-3 text-right text-aurora-mist/80">{{ activeVariantCount(product) }}</td>
                    <td class="px-4 py-3">
                      <span class="aurora-badge" [ngClass]="product.active ? 'bg-aurora-pine/20 text-aurora-pinebright' : 'bg-white/10 text-aurora-mist/70'">
                        {{ (product.active ? 'common.active' : 'common.inactive') | t }}
                      </span>
                    </td>
                    <td class="px-4 py-3">
                      <div class="flex items-center justify-end gap-2">
                        <button class="cursor-pointer rounded-ui p-2 text-aurora-mist/80 hover:bg-white/10 hover:text-white" type="button" (click)="edit(product)" [attr.aria-label]="('admin.products.editLabel' | t) + ' ' + product.name">
                          <lucide-icon [img]="Pencil" size="17" />
                        </button>
                        @if (product.active) {
                          <button class="cursor-pointer rounded-ui p-2 text-aurora-rose hover:bg-aurora-rose/15" type="button" (click)="deactivate(product)" [disabled]="rowBusy() === product.id" [attr.aria-label]="('admin.products.deactivateLabel' | t) + ' ' + product.name">
                            <lucide-icon [img]="CircleOff" size="17" />
                          </button>
                        } @else {
                          <button class="cursor-pointer rounded-ui p-2 text-aurora-pinebright hover:bg-aurora-pine/15" type="button" (click)="reactivate(product)" [disabled]="rowBusy() === product.id" [attr.aria-label]="('admin.products.reactivateLabel' | t) + ' ' + product.name">
                            <lucide-icon [img]="Power" size="17" />
                          </button>
                        }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="mt-8 grid gap-3 lg:hidden">
            @for (product of filtered(); track product.id) {
              <div class="rounded-ui border border-white/10 bg-white/[0.06] p-4">
                <div class="flex items-center justify-between gap-3">
                  <span class="flex items-center gap-2 font-extrabold text-white">
                    <lucide-icon class="text-aurora-pinebright" [img]="Layers" size="18" />
                    {{ product.name }}
                    @if (product.featured) {
                      <lucide-icon class="text-aurora-pinebright" [img]="Star" size="14" [attr.aria-label]="'admin.products.featured' | t" />
                    }
                  </span>
                  <span class="aurora-badge" [ngClass]="product.active ? 'bg-aurora-pine/20 text-aurora-pinebright' : 'bg-white/10 text-aurora-mist/70'">
                    {{ (product.active ? 'common.active' : 'common.inactive') | t }}
                  </span>
                </div>
                <dl class="mt-3 grid grid-cols-2 gap-2 text-sm">
                  <dt class="text-aurora-mist/60">{{ 'admin.products.col.category' | t }}</dt>
                  <dd class="text-right text-aurora-mist/80">{{ product.category.name }}</dd>
                  <dt class="text-aurora-mist/60">{{ 'admin.products.col.brand' | t }}</dt>
                  <dd class="text-right text-aurora-mist/80">{{ product.brand.name }}</dd>
                  <dt class="text-aurora-mist/60">{{ 'admin.products.col.price' | t }}</dt>
                  <dd class="text-right font-bold text-white">{{ formatPrice(product.basePrice) }}</dd>
                  <dt class="text-aurora-mist/60">{{ 'admin.products.col.variants' | t }}</dt>
                  <dd class="text-right text-aurora-mist/80">{{ activeVariantCount(product) }}</dd>
                </dl>
                <div class="mt-4 flex gap-2">
                  <button class="ui-button border border-white/10 bg-white/10 text-white hover:bg-white/15 flex-1" type="button" (click)="edit(product)">
                    <lucide-icon [img]="Pencil" size="16" />
                    {{ 'admin.products.edit' | t }}
                  </button>
                  @if (product.active) {
                    <button class="ui-button border border-white/10 bg-white/10 text-aurora-rose hover:bg-white/15 flex-1" type="button" (click)="deactivate(product)" [disabled]="rowBusy() === product.id">
                      <lucide-icon [img]="CircleOff" size="16" />
                      {{ 'admin.products.deactivate' | t }}
                    </button>
                  } @else {
                    <button class="ui-button border border-white/10 bg-white/10 text-aurora-pinebright hover:bg-white/15 flex-1" type="button" (click)="reactivate(product)" [disabled]="rowBusy() === product.id">
                      <lucide-icon [img]="Power" size="16" />
                      {{ 'admin.products.reactivate' | t }}
                    </button>
                  }
                </div>
              </div>
            }
          </div>
        }
      </div>
    </section>
  `
})
export class AdminProductsPageComponent {
  private readonly adminProductService = inject(AdminProductService);
  private readonly language = inject(LanguageService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly products = signal<AdminProductResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly rowBusy = signal<string | null>(null);

  readonly search = new FormControl('', { nonNullable: true });
  private readonly query = toSignal(this.search.valueChanges, { initialValue: '' });

  /** Client-side name filter; the list itself stays newest-first as returned by the server. */
  readonly filtered = computed<AdminProductResponse[]>(() => {
    const term = this.query().trim().toLowerCase();
    if (!term) {
      return this.products();
    }
    return this.products().filter((product) => product.name.toLowerCase().includes(term));
  });

  readonly CircleOff = CircleOff;
  readonly Layers = Layers;
  readonly Pencil = Pencil;
  readonly Plus = Plus;
  readonly Power = Power;
  readonly Search = Search;
  readonly Star = Star;

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.adminProductService.listProducts().subscribe({
      next: (products) => {
        this.products.set(products);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.language.translate('admin.products.error'));
        this.loading.set(false);
      }
    });
  }

  create(): void {
    this.router.navigate(['/admin/products/new']);
  }

  edit(product: AdminProductResponse): void {
    this.router.navigate(['/admin/products', product.id, 'edit']);
  }

  formatPrice(value: number): string {
    return new CurrencyPipe(DEFAULT_LOCALE).transform(value) ?? String(value);
  }

  activeVariantCount(product: AdminProductResponse): number {
    return product.variants.filter((variant) => variant.active).length;
  }

  deactivate(product: AdminProductResponse): void {
    this.rowBusy.set(product.id);
    this.adminProductService.deleteProduct(product.id).subscribe({
      next: () => {
        this.rowBusy.set(null);
        this.toast.success(this.language.translate('admin.products.deactivated'));
        this.load();
      },
      error: () => {
        this.rowBusy.set(null);
        this.toast.error(this.language.translate('admin.products.actionError'));
      }
    });
  }

  reactivate(product: AdminProductResponse): void {
    this.rowBusy.set(product.id);
    // Resend the FULL variant set (with ids) and the FULL image list so the PUT's
    // replace-all/variant-sync semantics don't drop or deactivate anything — we only
    // flip `active` to true. Inactive child variants stay inactive (omitted from resend
    // by keeping their state via id), but per-variant active flags are preserved.
    const request: AdminProductRequest = {
      name: product.name,
      slug: product.slug,
      description: product.description,
      shortDescription: product.shortDescription,
      basePrice: product.basePrice,
      active: true,
      featured: product.featured,
      categoryId: product.category.id,
      brandId: product.brand.id,
      variants: product.variants.map((variant) => ({
        id: variant.id,
        sku: variant.sku,
        name: variant.name,
        priceOverride: variant.priceOverride,
        attributesJson: variant.attributesJson,
        active: variant.active
      })),
      images: product.images.map((image) => ({
        url: image.url,
        altText: image.altText,
        position: image.position,
        mainImage: image.mainImage
      }))
    };
    this.adminProductService.updateProduct(product.id, request).subscribe({
      next: () => {
        this.rowBusy.set(null);
        this.toast.success(this.language.translate('admin.products.reactivated'));
        this.load();
      },
      error: () => {
        this.rowBusy.set(null);
        this.toast.error(this.language.translate('admin.products.actionError'));
      }
    });
  }
}
