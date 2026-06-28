import { Component, computed, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, ArrowLeft, ImagePlus, Layers, ListPlus, Trash2 } from 'lucide-angular';
import { LanguageService } from '../../../core/i18n/language.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import {
  AdminBrandResponse,
  AdminCategoryResponse,
  AdminProductImageRequest,
  AdminProductRequest,
  AdminProductResponse,
  AdminProductVariantRequest
} from '../../../core/models/admin-product.model';
import { AdminProductService } from '../../../services/admin-product.service';
import { ToastService } from '../../../services/toast.service';
import { StatePanelComponent } from '../../../shared/state-panel/state-panel.component';

/** basePrice / priceOverride: up to 10 integer digits and at most 2 decimals, >= 0. */
const PRICE_PATTERN = /^\d{1,10}(\.\d{1,2})?$/;
/** Image URL: absolute http(s). */
const IMAGE_URL_PATTERN = /^https?:\/\/.+/;

@Component({
  selector: 'app-admin-product-form-page',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="px-4 py-8 sm:px-6 lg:px-8">
      <div class="max-w-5xl">
        <a routerLink="/admin/products" class="inline-flex cursor-pointer items-center gap-2 text-sm font-semibold text-aurora-pinebright hover:underline">
          <lucide-icon [img]="ArrowLeft" size="17" />
          {{ 'admin.products.title' | t }}
        </a>

        @if (loading()) {
          <div class="mt-6 grid gap-6">
            <div class="h-72 animate-pulse rounded-ui bg-white/10"></div>
            <div class="h-48 animate-pulse rounded-ui bg-white/10"></div>
          </div>
        } @else if (loadError()) {
          <div class="mt-6">
            <app-state-panel mode="error" title="{{ 'admin.products.errorTitle' | t }}" [message]="loadError()!" />
            <a routerLink="/admin/products" class="ui-button mt-6 inline-flex border border-white/10 bg-white/10 text-white hover:bg-white/15">
              {{ 'admin.products.title' | t }}
            </a>
          </div>
        } @else {
          <form class="mt-6 space-y-6" [formGroup]="form" (ngSubmit)="save()">
            <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6">
              <p class="text-xs font-bold uppercase tracking-[0.18em] text-aurora-pinebright">{{ 'admin.products.eyebrow' | t }}</p>
              <h1 class="mt-3 text-3xl font-extrabold tracking-normal text-white sm:text-4xl">
                {{ (editingId() ? 'admin.products.editTitle' : 'admin.products.createTitle') | t }}
              </h1>

              <div class="mt-6 grid gap-5 sm:grid-cols-2">
                <label class="block sm:col-span-2">
                  <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.field.name' | t }}</span>
                  <input
                    class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                    formControlName="name"
                    type="text"
                    maxlength="180"
                    [attr.aria-invalid]="invalid('name')"
                    [attr.aria-describedby]="invalid('name') ? 'product-name-error' : null"
                  />
                  @if (invalid('name')) {
                    <span id="product-name-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ nameError() | t }}</span>
                  }
                </label>

                <label class="block sm:col-span-2">
                  <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.field.slug' | t }}</span>
                  <input
                    class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                    formControlName="slug"
                    type="text"
                    maxlength="220"
                    [placeholder]="'admin.products.field.slugPlaceholder' | t"
                    [attr.aria-invalid]="invalid('slug')"
                    [attr.aria-describedby]="invalid('slug') ? 'product-slug-error' : null"
                  />
                  <span class="mt-1 block text-xs text-aurora-mist/60">{{ 'admin.products.field.slugHint' | t }}</span>
                  @if (invalid('slug')) {
                    <span id="product-slug-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ slugError() | t }}</span>
                  }
                </label>

                <label class="block sm:col-span-1">
                  <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.field.category' | t }}</span>
                  <select
                    class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white [&>option]:text-aurora-ink"
                    formControlName="categoryId"
                    [attr.aria-invalid]="invalid('categoryId')"
                    [attr.aria-describedby]="invalid('categoryId') ? 'product-category-error' : null"
                  >
                    <option value="" disabled>{{ 'admin.products.field.selectCategory' | t }}</option>
                    @for (category of categories(); track category.id) {
                      <option [value]="category.id">{{ category.name }}</option>
                    }
                  </select>
                  @if (invalid('categoryId')) {
                    <span id="product-category-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.products.error.categoryRequired' | t }}</span>
                  }
                </label>

                <label class="block sm:col-span-1">
                  <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.field.brand' | t }}</span>
                  <select
                    class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white [&>option]:text-aurora-ink"
                    formControlName="brandId"
                    [attr.aria-invalid]="invalid('brandId')"
                    [attr.aria-describedby]="invalid('brandId') ? 'product-brand-error' : null"
                  >
                    <option value="" disabled>{{ 'admin.products.field.selectBrand' | t }}</option>
                    @for (brand of brands(); track brand.id) {
                      <option [value]="brand.id">{{ brand.name }}</option>
                    }
                  </select>
                  @if (invalid('brandId')) {
                    <span id="product-brand-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.products.error.brandRequired' | t }}</span>
                  }
                </label>

                <label class="block sm:col-span-1">
                  <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.field.basePrice' | t }}</span>
                  <input
                    class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                    formControlName="basePrice"
                    type="number"
                    step="0.01"
                    min="0"
                    [attr.aria-invalid]="invalid('basePrice')"
                    [attr.aria-describedby]="invalid('basePrice') ? 'product-baseprice-error' : null"
                  />
                  @if (invalid('basePrice')) {
                    <span id="product-baseprice-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.products.error.basePrice' | t }}</span>
                  }
                </label>

                <div class="flex flex-wrap items-end gap-6 sm:col-span-1 sm:pb-3">
                  <label class="flex items-center gap-3">
                    <input class="h-5 w-5 cursor-pointer rounded border-white/20 bg-white/[0.05] accent-aurora-pine" formControlName="active" type="checkbox" />
                    <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.field.active' | t }}</span>
                  </label>
                  <label class="flex items-center gap-3">
                    <input class="h-5 w-5 cursor-pointer rounded border-white/20 bg-white/[0.05] accent-aurora-pine" formControlName="featured" type="checkbox" />
                    <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.field.featured' | t }}</span>
                  </label>
                </div>

                <label class="block sm:col-span-2">
                  <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.field.shortDescription' | t }}</span>
                  <input
                    class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                    formControlName="shortDescription"
                    type="text"
                    maxlength="500"
                    [attr.aria-invalid]="invalid('shortDescription')"
                    [attr.aria-describedby]="invalid('shortDescription') ? 'product-shortdesc-error' : null"
                  />
                  @if (invalid('shortDescription')) {
                    <span id="product-shortdesc-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.products.error.shortDescriptionLength' | t }}</span>
                  }
                </label>

                <label class="block sm:col-span-2">
                  <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.field.description' | t }}</span>
                  <textarea
                    class="ui-input mt-2 h-auto min-h-28 resize-y border-white/10 bg-white/[0.05] py-2 text-white"
                    formControlName="description"
                    rows="4"
                    maxlength="5000"
                    [attr.aria-invalid]="invalid('description')"
                    [attr.aria-describedby]="invalid('description') ? 'product-desc-error' : null"
                  ></textarea>
                  @if (invalid('description')) {
                    <span id="product-desc-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.products.error.descriptionLength' | t }}</span>
                  }
                </label>
              </div>
            </div>

            <!-- Variants -->
            <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6">
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div class="flex items-center gap-2">
                  <lucide-icon class="text-aurora-pinebright" [img]="Layers" size="20" />
                  <h2 class="text-lg font-bold text-white">{{ 'admin.products.variants.title' | t }}</h2>
                </div>
                <button class="ui-button border border-white/10 bg-white/10 text-white hover:bg-white/15" type="button" (click)="addVariant()" [disabled]="variants.length >= 100">
                  <lucide-icon [img]="ListPlus" size="16" />
                  {{ 'admin.products.variants.add' | t }}
                </button>
              </div>
              <p class="mt-2 text-xs text-aurora-mist/60">{{ 'admin.products.variants.hint' | t }}</p>

              @if (variants.length === 0) {
                <p class="mt-5 rounded-ui bg-white/[0.05] px-4 py-3 text-sm text-aurora-mist/70">{{ 'admin.products.variants.empty' | t }}</p>
              } @else {
                <div class="mt-5 grid gap-4" formArrayName="variants">
                  @for (variant of variants.controls; track variant; let i = $index) {
                    <div class="rounded-ui border border-white/10 bg-white/[0.04] p-4" [formGroupName]="i">
                      <div class="grid gap-4 sm:grid-cols-2">
                        <label class="block">
                          <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.variants.sku' | t }}</span>
                          <input
                            class="ui-input mt-2 border-white/10 bg-white/[0.05] uppercase text-white"
                            formControlName="sku"
                            type="text"
                            maxlength="80"
                            autocomplete="off"
                            [attr.aria-invalid]="variantInvalid(i, 'sku')"
                          />
                          @if (variantInvalid(i, 'sku')) {
                            <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ variantSkuError(i) | t }}</span>
                          }
                        </label>
                        <label class="block">
                          <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.variants.name' | t }}</span>
                          <input
                            class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                            formControlName="name"
                            type="text"
                            maxlength="180"
                            [attr.aria-invalid]="variantInvalid(i, 'name')"
                          />
                          @if (variantInvalid(i, 'name')) {
                            <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.products.variants.nameRequired' | t }}</span>
                          }
                        </label>
                        <label class="block">
                          <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.variants.priceOverride' | t }}</span>
                          <input
                            class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                            formControlName="priceOverride"
                            type="number"
                            step="0.01"
                            min="0"
                            [placeholder]="'admin.products.variants.useBase' | t"
                            [attr.aria-invalid]="variantInvalid(i, 'priceOverride')"
                          />
                          @if (variantInvalid(i, 'priceOverride')) {
                            <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.products.variants.priceInvalid' | t }}</span>
                          }
                        </label>
                        <label class="block">
                          <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.variants.attributes' | t }}</span>
                          <input
                            class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                            formControlName="attributesJson"
                            type="text"
                            maxlength="2000"
                            [placeholder]="'admin.products.variants.attributesPlaceholder' | t"
                            [attr.aria-invalid]="variantInvalid(i, 'attributesJson')"
                          />
                          @if (variantInvalid(i, 'attributesJson')) {
                            <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.products.variants.attributesLength' | t }}</span>
                          }
                        </label>
                      </div>
                      <div class="mt-4 flex items-center justify-between gap-3">
                        <label class="flex items-center gap-3">
                          <input class="h-5 w-5 cursor-pointer rounded border-white/20 bg-white/[0.05] accent-aurora-pine" formControlName="active" type="checkbox" />
                          <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.variants.active' | t }}</span>
                        </label>
                        <button class="cursor-pointer rounded-ui p-2 text-aurora-rose hover:bg-aurora-rose/15" type="button" (click)="removeVariant(i)" [attr.aria-label]="'admin.products.variants.remove' | t">
                          <lucide-icon [img]="Trash2" size="17" />
                        </button>
                      </div>
                    </div>
                  }
                </div>
              }
            </div>

            <!-- Images -->
            <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6">
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div class="flex items-center gap-2">
                  <lucide-icon class="text-aurora-pinebright" [img]="ImagePlus" size="20" />
                  <h2 class="text-lg font-bold text-white">{{ 'admin.products.images.title' | t }}</h2>
                </div>
                <button class="ui-button border border-white/10 bg-white/10 text-white hover:bg-white/15" type="button" (click)="addImage()" [disabled]="images.length >= 50">
                  <lucide-icon [img]="ImagePlus" size="16" />
                  {{ 'admin.products.images.add' | t }}
                </button>
              </div>
              <p class="mt-2 text-xs text-aurora-mist/60">{{ 'admin.products.images.hint' | t }}</p>

              @if (images.length === 0) {
                <p class="mt-5 rounded-ui bg-white/[0.05] px-4 py-3 text-sm text-aurora-mist/70">{{ 'admin.products.images.empty' | t }}</p>
              } @else {
                <div class="mt-5 grid gap-4" formArrayName="images">
                  @for (image of images.controls; track image; let i = $index) {
                    <div class="rounded-ui border border-white/10 bg-white/[0.04] p-4" [formGroupName]="i">
                      <div class="grid gap-4 sm:grid-cols-[1fr_160px]">
                        <label class="block">
                          <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.images.url' | t }}</span>
                          <input
                            class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                            formControlName="url"
                            type="url"
                            maxlength="1000"
                            placeholder="https://"
                            [attr.aria-invalid]="imageInvalid(i, 'url')"
                          />
                          @if (imageInvalid(i, 'url')) {
                            <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ imageUrlError(i) | t }}</span>
                          }
                        </label>
                        <label class="block">
                          <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.images.position' | t }}</span>
                          <input
                            class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                            formControlName="position"
                            type="number"
                            step="1"
                            min="0"
                            [attr.aria-invalid]="imageInvalid(i, 'position')"
                          />
                          @if (imageInvalid(i, 'position')) {
                            <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.products.images.positionInvalid' | t }}</span>
                          }
                        </label>
                      </div>
                      <label class="mt-4 block">
                        <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.images.altText' | t }}</span>
                        <input
                          class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                          formControlName="altText"
                          type="text"
                          maxlength="255"
                          [attr.aria-invalid]="imageInvalid(i, 'altText')"
                        />
                        @if (imageInvalid(i, 'altText')) {
                          <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.products.images.altTextLength' | t }}</span>
                        }
                      </label>
                      <div class="mt-4 flex items-center justify-between gap-3">
                        <label class="flex items-center gap-3">
                          <input
                            class="h-5 w-5 cursor-pointer rounded border-white/20 bg-white/[0.05] accent-aurora-pine"
                            type="checkbox"
                            [checked]="image.get('mainImage')!.value"
                            (change)="setMainImage(i, $event)"
                          />
                          <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.products.images.main' | t }}</span>
                        </label>
                        <button class="cursor-pointer rounded-ui p-2 text-aurora-rose hover:bg-aurora-rose/15" type="button" (click)="removeImage(i)" [attr.aria-label]="'admin.products.images.remove' | t">
                          <lucide-icon [img]="Trash2" size="17" />
                        </button>
                      </div>
                    </div>
                  }
                </div>
              }
            </div>

            <div class="flex flex-wrap gap-3">
              <a routerLink="/admin/products" class="ui-button border border-white/10 bg-white/10 text-white hover:bg-white/15 flex-1">
                {{ 'common.cancel' | t }}
              </a>
              <button class="ui-button ui-button-primary flex-1" type="submit" [disabled]="saving()">
                {{ saving() ? ('admin.products.saving' | t) : ('admin.products.save' | t) }}
              </button>
            </div>
          </form>
        }
      </div>
    </section>
  `
})
export class AdminProductFormPageComponent {
  private readonly adminProductService = inject(AdminProductService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly language = inject(LanguageService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly categories = signal<AdminCategoryResponse[]>([]);
  readonly brands = signal<AdminBrandResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly duplicateSlug = signal(false);
  /** Variant-row index → true when its SKU collided server-side. */
  readonly duplicateSku = signal<Set<number>>(new Set());

  readonly ArrowLeft = ArrowLeft;
  readonly ImagePlus = ImagePlus;
  readonly Layers = Layers;
  readonly ListPlus = ListPlus;
  readonly Trash2 = Trash2;

  readonly form = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(180)]],
    slug: ['', [Validators.maxLength(220)]],
    shortDescription: ['', [Validators.maxLength(500)]],
    description: ['', [Validators.maxLength(5000)]],
    basePrice: [null as number | null, [Validators.required, Validators.min(0), Validators.pattern(PRICE_PATTERN)]],
    active: [true],
    featured: [false],
    categoryId: ['', [Validators.required]],
    brandId: ['', [Validators.required]],
    variants: this.formBuilder.array<FormGroup>([]),
    images: this.formBuilder.array<FormGroup>([])
  });

  get variants(): FormArray<FormGroup> {
    return this.form.controls.variants;
  }

  get images(): FormArray<FormGroup> {
    return this.form.controls.images;
  }

  private id: string | null = null;

  constructor() {
    this.id = this.route.snapshot.paramMap.get('id');
    this.editingId.set(this.id);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    const lookups$ = forkJoin({
      categories: this.adminProductService.listCategories(),
      brands: this.adminProductService.listBrands()
    });

    if (!this.id) {
      lookups$.subscribe({
        next: ({ categories, brands }) => {
          this.categories.set(categories);
          this.brands.set(brands);
          this.loading.set(false);
        },
        error: () => {
          this.loadError.set(this.language.translate('admin.products.error'));
          this.loading.set(false);
        }
      });
      return;
    }

    forkJoin({
      categories: this.adminProductService.listCategories(),
      brands: this.adminProductService.listBrands(),
      product: this.adminProductService.getProduct(this.id)
    }).subscribe({
      next: ({ categories, brands, product }) => {
        this.categories.set(categories);
        this.brands.set(brands);
        this.prefill(product);
        this.loading.set(false);
      },
      error: (err: { status?: number }) => {
        this.loadError.set(this.language.translate(err?.status === 404 ? 'admin.products.notFound' : 'admin.products.error'));
        this.loading.set(false);
      }
    });
  }

  private prefill(product: AdminProductResponse): void {
    this.form.patchValue({
      name: product.name,
      slug: product.slug,
      shortDescription: product.shortDescription ?? '',
      description: product.description ?? '',
      basePrice: product.basePrice,
      active: product.active,
      featured: product.featured,
      categoryId: product.category.id,
      brandId: product.brand.id
    });
    // Resend EVERY existing variant (with its id) so the PUT keeps them — omitting one
    // would deactivate it. The form is the single source of the intended variant set.
    this.variants.clear();
    for (const variant of product.variants) {
      this.variants.push(
        this.buildVariantGroup({
          id: variant.id,
          sku: variant.sku,
          name: variant.name,
          priceOverride: variant.priceOverride,
          attributesJson: variant.attributesJson ?? '',
          active: variant.active
        })
      );
    }
    // Images are replace-all on PUT, so prefill the full set.
    this.images.clear();
    for (const image of product.images) {
      this.images.push(
        this.buildImageGroup({
          url: image.url,
          altText: image.altText ?? '',
          position: image.position,
          mainImage: image.mainImage
        })
      );
    }
  }

  private buildVariantGroup(value: {
    id: string | null;
    sku: string;
    name: string;
    priceOverride: number | null;
    attributesJson: string;
    active: boolean;
  }): FormGroup {
    return this.formBuilder.nonNullable.group({
      id: [value.id as string | null],
      sku: [value.sku, [Validators.required, Validators.maxLength(80)]],
      name: [value.name, [Validators.required, Validators.maxLength(180)]],
      priceOverride: [value.priceOverride as number | null, [Validators.min(0), Validators.pattern(PRICE_PATTERN)]],
      attributesJson: [value.attributesJson, [Validators.maxLength(2000)]],
      active: [value.active]
    });
  }

  private buildImageGroup(value: { url: string; altText: string; position: number | null; mainImage: boolean }): FormGroup {
    return this.formBuilder.nonNullable.group({
      url: [value.url, [Validators.required, Validators.maxLength(1000), Validators.pattern(IMAGE_URL_PATTERN)]],
      altText: [value.altText, [Validators.maxLength(255)]],
      position: [value.position as number | null, [Validators.min(0)]],
      mainImage: [value.mainImage]
    });
  }

  addVariant(): void {
    if (this.variants.length >= 100) {
      return;
    }
    this.variants.push(
      this.buildVariantGroup({ id: null, sku: '', name: '', priceOverride: null, attributesJson: '', active: true })
    );
  }

  removeVariant(index: number): void {
    this.variants.removeAt(index);
    this.clearDuplicateSku(index);
  }

  addImage(): void {
    if (this.images.length >= 50) {
      return;
    }
    // First image added defaults to the main image when none is set yet.
    const isFirstMain = !this.images.controls.some((group) => group.get('mainImage')!.value);
    this.images.push(this.buildImageGroup({ url: '', altText: '', position: null, mainImage: isFirstMain }));
  }

  removeImage(index: number): void {
    this.images.removeAt(index);
  }

  /** At most one main image: checking one clears the others. */
  setMainImage(index: number, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.images.controls.forEach((group, i) => {
      group.get('mainImage')!.setValue(i === index ? checked : false);
      group.get('mainImage')!.markAsDirty();
    });
  }

  invalid(name: 'name' | 'slug' | 'shortDescription' | 'description' | 'basePrice' | 'categoryId' | 'brandId'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.dirty || control.touched);
  }

  nameError(): string {
    return this.form.controls.name.hasError('maxlength') ? 'admin.products.error.nameLength' : 'admin.products.error.nameRequired';
  }

  slugError(): string {
    return this.duplicateSlug() ? 'admin.products.error.slugDuplicate' : 'admin.products.error.slugLength';
  }

  variantInvalid(index: number, control: 'sku' | 'name' | 'priceOverride' | 'attributesJson'): boolean {
    const group = this.variants.at(index);
    const field = group.get(control);
    return !!field && field.invalid && (field.dirty || field.touched);
  }

  variantSkuError(index: number): string {
    if (this.duplicateSku().has(index)) {
      return 'admin.products.variants.skuDuplicate';
    }
    return this.variants.at(index).get('sku')!.hasError('maxlength')
      ? 'admin.products.variants.skuLength'
      : 'admin.products.variants.skuRequired';
  }

  imageInvalid(index: number, control: 'url' | 'altText' | 'position'): boolean {
    const group = this.images.at(index);
    const field = group.get(control);
    return !!field && field.invalid && (field.dirty || field.touched);
  }

  imageUrlError(index: number): string {
    const field = this.images.at(index).get('url')!;
    if (field.hasError('required')) {
      return 'admin.products.images.urlRequired';
    }
    if (field.hasError('maxlength')) {
      return 'admin.products.images.urlLength';
    }
    return 'admin.products.images.urlPattern';
  }

  /** Builds the POST/PUT body from the form, sending the FULL variant and image sets. */
  buildRequest(): AdminProductRequest {
    const raw = this.form.getRawValue();
    const slug = raw.slug.trim();
    const shortDescription = raw.shortDescription.trim();
    const description = raw.description.trim();

    const variants: AdminProductVariantRequest[] = this.variants.controls.map((group) => {
      const value = group.getRawValue() as {
        id: string | null;
        sku: string;
        name: string;
        priceOverride: number | null;
        attributesJson: string;
        active: boolean;
      };
      const attributes = value.attributesJson.trim();
      return {
        id: value.id ?? null,
        sku: value.sku.trim(),
        name: value.name.trim(),
        priceOverride: value.priceOverride ?? null,
        attributesJson: attributes ? attributes : null,
        active: value.active
      };
    });

    const images: AdminProductImageRequest[] = this.images.controls.map((group) => {
      const value = group.getRawValue() as { url: string; altText: string; position: number | null; mainImage: boolean };
      const altText = value.altText.trim();
      return {
        url: value.url.trim(),
        altText: altText ? altText : null,
        position: value.position ?? null,
        mainImage: value.mainImage
      };
    });

    return {
      name: raw.name.trim(),
      slug: slug ? slug : undefined,
      shortDescription: shortDescription ? shortDescription : null,
      description: description ? description : null,
      basePrice: raw.basePrice ?? 0,
      active: raw.active,
      featured: raw.featured,
      categoryId: raw.categoryId,
      brandId: raw.brandId,
      variants,
      images
    };
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.error(this.language.translate('admin.products.error.validation'));
      return;
    }

    const request = this.buildRequest();
    this.saving.set(true);
    this.duplicateSlug.set(false);
    this.duplicateSku.set(new Set());

    const id = this.editingId();
    const call = id ? this.adminProductService.updateProduct(id, request) : this.adminProductService.createProduct(request);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(this.language.translate(id ? 'admin.products.updated' : 'admin.products.created'));
        this.router.navigate(['/admin/products']);
      },
      error: (err: { status?: number; error?: { code?: string; validationErrors?: Record<string, string> } }) => {
        this.saving.set(false);
        this.handleSaveError(err);
      }
    });
  }

  private handleSaveError(err: { status?: number; error?: { code?: string; validationErrors?: Record<string, string> } }): void {
    const code = err.error?.code;
    if (err.status === 409 && code === 'PRODUCT_SLUG_EXISTS') {
      this.duplicateSlug.set(true);
      this.form.controls.slug.markAsTouched();
      this.form.controls.slug.setErrors({ duplicate: true });
      this.toast.error(this.language.translate('admin.products.error.slugDuplicate'));
      return;
    }
    if (err.status === 409 && code === 'VARIANT_SKU_EXISTS') {
      this.flagAllVariantSkus();
      this.toast.error(this.language.translate('admin.products.variants.skuDuplicate'));
      return;
    }
    if (err.status === 400 && code === 'DUPLICATE_VARIANT_SKU_IN_REQUEST') {
      this.flagAllVariantSkus();
      this.toast.error(this.language.translate('admin.products.variants.skuDuplicateInRequest'));
      return;
    }
    if (err.status === 400 && code === 'MULTIPLE_MAIN_PRODUCT_IMAGES') {
      this.toast.error(this.language.translate('admin.products.images.multipleMain'));
      return;
    }
    if (err.status === 404) {
      // An inactive category/brand was referenced (or the product vanished).
      this.toast.error(this.language.translate('admin.products.error.referenceMissing'));
      return;
    }
    if (err.status === 400 && err.error?.validationErrors) {
      this.applyValidationErrors(err.error.validationErrors);
      this.toast.error(this.language.translate('admin.products.error.validation'));
      return;
    }
    this.toast.error(this.language.translate('admin.products.saveError'));
  }

  /** Server can't tell us which row collided, so flag every SKU field for review. */
  private flagAllVariantSkus(): void {
    const indexes = new Set<number>();
    this.variants.controls.forEach((group, index) => {
      const sku = group.get('sku')!;
      sku.markAsTouched();
      sku.setErrors({ duplicate: true });
      indexes.add(index);
    });
    this.duplicateSku.set(indexes);
  }

  private clearDuplicateSku(removedIndex: number): void {
    const current = this.duplicateSku();
    if (current.size === 0) {
      return;
    }
    const next = new Set<number>();
    for (const i of current) {
      if (i < removedIndex) {
        next.add(i);
      } else if (i > removedIndex) {
        next.add(i - 1);
      }
    }
    this.duplicateSku.set(next);
  }

  private applyValidationErrors(errors: Record<string, string>): void {
    for (const field of Object.keys(errors)) {
      const control = this.form.get(field);
      if (control) {
        control.markAsTouched();
        control.setErrors({ server: true });
      }
    }
  }
}
