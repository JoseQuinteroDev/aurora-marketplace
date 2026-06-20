import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
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
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
import { Product } from '../../core/models/product.model';
import { Review } from '../../core/models/review.model';
import { cartErrorKey } from '../../core/util/cart-errors';
import { firstActiveVariantId, productGallery, productImage, productImageAlt } from '../../core/util/product-media';
import { AuthService } from '../../services/auth.service';
import { CartService } from '../../services/cart.service';
import { CatalogService } from '../../services/catalog.service';
import { ReviewService } from '../../services/review.service';
import { ToastService } from '../../services/toast.service';
import { WishlistService } from '../../services/wishlist.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

type ProductTab = 'description' | 'specs' | 'reviews';

@Component({
  selector: 'app-product-detail-page',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell py-10 sm:py-12">
      <a routerLink="/catalog" class="premium-link inline-flex items-center gap-2 text-sm">
        <lucide-icon [img]="ArrowLeft" size="17" />
        {{ 'product.back' | t }}
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
          <app-state-panel mode="error" title="{{ 'product.unavailable' | t }}" [message]="error()!" />
        </div>
      } @else if (product(); as item) {
        <div class="mt-8 grid gap-8 lg:grid-cols-[0.95fr_1.05fr]">
          <div class="space-y-4">
            <div class="premium-shell relative overflow-hidden p-3">
              <img class="aspect-square w-full rounded-ui object-cover" [src]="selectedImage() || imageUrl(item)" [alt]="imageAlt(item)" />
              <div class="absolute left-6 top-6 flex flex-wrap gap-2">
                @if (item.featured) {
                  <span class="aurora-badge border-white/60 bg-white/90 text-aurora-ink">{{ 'product.featured' | t }}</span>
                }
                <span class="aurora-badge border-white/60 bg-white/90 text-aurora-ink">{{ item.category.name }}</span>
              </div>
            </div>

            <div class="grid grid-cols-4 gap-3">
              @for (image of galleryUrls(item); track image) {
                <button class="group overflow-hidden rounded-ui border border-aurora-line bg-white p-1 transition duration-200 hover:border-aurora-amber dark:border-white/10 dark:bg-white/10" type="button" (click)="selectedImage.set(image)" [attr.aria-label]="'a11y.selectImage' | t">
                  <img loading="lazy" class="aspect-square w-full rounded-ui object-cover transition duration-300 group-hover:scale-[1.04] motion-reduce:transition-none motion-reduce:group-hover:scale-100" [src]="image" [alt]="item.name" />
                </button>
              }
            </div>
          </div>

          <div>
            <div class="lg:sticky lg:top-28">
              <div class="space-y-5">
                <div>
                  <p class="section-kicker">{{ item.brand.name }} / {{ item.category.name }}</p>
                  <h1 class="mt-3 text-4xl font-semibold leading-tight text-aurora-ink sm:text-5xl dark:text-white">{{ item.name }}</h1>
                  <div class="mt-4 flex flex-wrap items-center gap-3">
                    @if (reviews().length > 0) {
                      <span class="inline-flex items-center gap-1.5 text-sm font-black text-aurora-pine dark:text-aurora-pinebright">
                        <lucide-icon [img]="Star" size="16" />
                        {{ averageRating() }}
                        <span class="font-semibold text-aurora-muted dark:text-stone-300">· {{ reviews().length }} {{ (reviews().length === 1 ? 'product.reviewsCountLabelSingular' : 'product.reviewsCountLabel') | t }}</span>
                      </span>
                    } @else {
                      <button type="button" class="inline-flex cursor-pointer items-center gap-1.5 text-sm font-semibold text-aurora-muted transition-colors hover:text-aurora-gold dark:text-stone-300" (click)="activeTab.set('reviews')">
                        <lucide-icon [img]="Star" size="16" />
                        {{ 'product.beFirstReview' | t }}
                      </button>
                    }
                    <span class="h-1 w-1 rounded-full bg-aurora-line dark:bg-white/20"></span>
                    <span class="text-sm font-semibold text-aurora-muted dark:text-stone-300">{{ activeVariantCount(item) }} {{ (activeVariantCount(item) === 1 ? 'product.variantLabelSingular' : 'product.variantsLabel') | t }}</span>
                    <span class="aurora-badge bg-aurora-pine/10 text-aurora-pine dark:bg-aurora-pine/15 dark:text-aurora-pinebright">
                      <lucide-icon [img]="BadgeCheck" size="14" />
                      {{ 'product.inStock' | t }}
                    </span>
                  </div>
                </div>

                <div class="surface-panel p-5">
                  <div class="flex flex-wrap items-end justify-between gap-4">
                    <div>
                      <p class="text-sm font-semibold text-aurora-muted dark:text-stone-400">{{ 'product.startingAt' | t }}</p>
                      <p class="mt-1 text-4xl font-black text-aurora-ink dark:text-white">{{ displayPrice(item) | currency }}</p>
                    </div>
                    <button class="ui-button h-11 w-11 min-h-11 p-0" [class.ui-button-primary]="wishlist.isWishlisted(item.id)" [class.ui-button-secondary]="!wishlist.isWishlisted(item.id)" type="button" [disabled]="wishlistLoading()" (click)="toggleWishlist(item)" [attr.aria-label]="'product.save' | t">
                      <lucide-icon [img]="Heart" size="18" />
                    </button>
                  </div>

                  <div class="mt-5 grid gap-3 sm:grid-cols-2">
                    <button class="ui-button ui-button-primary w-full" type="button" [disabled]="cartLoading() || !selectedVariantId(item)" (click)="addToCart(item)">
                      <lucide-icon [img]="ShoppingBag" size="18" />
                      {{ cartLoading() ? ('product.adding' | t) : ('product.addToCart' | t) }}
                    </button>
                    <button class="ui-button ui-button-accent w-full" type="button" [disabled]="cartLoading() || !selectedVariantId(item)" (click)="buyNow(item)">
                      <lucide-icon [img]="Zap" size="18" />
                      {{ 'product.buyNow' | t }}
                    </button>
                  </div>
                </div>

                <div class="surface-panel p-5">
                  <p class="text-sm font-black text-aurora-ink dark:text-white">{{ 'product.variant' | t }}</p>
                  <div class="mt-3 grid gap-2 sm:grid-cols-2">
                    @for (variant of item.variants; track variant.id) {
                      <button class="rounded-ui border border-aurora-line bg-white p-3 text-left transition duration-200 hover:border-aurora-amber hover:shadow-sm dark:border-white/10 dark:bg-white/10" [class.border-aurora-amber]="variant.id === selectedVariant()" type="button" (click)="selectedVariant.set(variant.id)">
                        <span class="block text-sm font-black text-aurora-ink dark:text-white">{{ variant.name }}</span>
                        <span class="mt-1 block text-xs font-semibold text-aurora-muted dark:text-stone-400">{{ variant.sku }}</span>
                        <span class="mt-2 block text-sm font-black text-aurora-gold dark:text-aurora-pinebright">{{ variant.effectivePrice | currency }}</span>
                      </button>
                    } @empty {
                      <p class="text-sm text-aurora-muted dark:text-stone-300">{{ 'product.defaultVariant' | t }}</p>
                    }
                  </div>
                </div>

                <div class="grid gap-3 sm:grid-cols-3">
                  @for (promise of promises; track promise.title) {
                    <div class="surface-panel p-4">
                      <lucide-icon class="text-aurora-ocean dark:text-aurora-pinebright" [img]="promise.icon" size="20" />
                      <p class="mt-3 text-sm font-black text-aurora-ink dark:text-white">{{ promise.title | t }}</p>
                      <p class="mt-1 text-xs leading-5 text-aurora-muted dark:text-stone-300">{{ promise.copy | t }}</p>
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
              {{ 'product.description' | t }}
            </button>
            <button class="ui-button justify-start" [class.ui-button-primary]="activeTab() === 'specs'" [class.ui-button-secondary]="activeTab() !== 'specs'" type="button" (click)="activeTab.set('specs')">
              <lucide-icon [img]="ListChecks" size="17" />
              {{ 'product.specs' | t }}
            </button>
            <button class="ui-button justify-start" [class.ui-button-primary]="activeTab() === 'reviews'" [class.ui-button-secondary]="activeTab() !== 'reviews'" type="button" (click)="activeTab.set('reviews')">
              <lucide-icon [img]="Star" size="17" />
              {{ 'product.reviews' | t }}
            </button>
          </div>

          <div class="p-3 sm:p-5">
            @if (activeTab() === 'description') {
              <p class="max-w-3xl text-sm leading-7 text-aurora-muted dark:text-stone-300">
                {{ item.description || item.shortDescription || ('product.descriptionFallback' | t) }}
              </p>
            } @else if (activeTab() === 'specs') {
              <div class="grid gap-3 sm:grid-cols-3">
                <div class="rounded-ui bg-stone-50 p-4 dark:bg-white/5">
                  <p class="text-xs font-bold uppercase tracking-[0.12em] text-aurora-muted dark:text-stone-400">{{ 'product.specBrand' | t }}</p>
                  <p class="mt-2 font-black text-aurora-ink dark:text-white">{{ item.brand.name }}</p>
                </div>
                <div class="rounded-ui bg-stone-50 p-4 dark:bg-white/5">
                  <p class="text-xs font-bold uppercase tracking-[0.12em] text-aurora-muted dark:text-stone-400">{{ 'catalog.categories' | t }}</p>
                  <p class="mt-2 font-black text-aurora-ink dark:text-white">{{ item.category.name }}</p>
                </div>
                <div class="rounded-ui bg-stone-50 p-4 dark:bg-white/5">
                  <p class="text-xs font-bold uppercase tracking-[0.12em] text-aurora-muted dark:text-stone-400">{{ 'orders.status' | t }}</p>
                  <p class="mt-2 font-black text-aurora-ink dark:text-white">{{ item.active ? ('common.active' | t) : ('common.inactive' | t) }}</p>
                </div>
              </div>
            } @else {
              <div class="grid gap-6 lg:grid-cols-[1fr_360px]">
                <div>
                  <h2 class="text-xl font-black text-aurora-ink dark:text-white">{{ 'product.reviewsTitle' | t }}</h2>
                  @if (reviewsLoading()) {
                    <div class="mt-4 space-y-3">
                      @for (review of [1, 2]; track review) {
                        <div class="skeleton-line h-28 rounded-ui"></div>
                      }
                    </div>
                  } @else if (reviewsError()) {
                    <div class="mt-4">
                      <app-state-panel mode="error" title="{{ 'common.error' | t }}" [message]="reviewsError()!" />
                    </div>
                  } @else if (reviews().length === 0) {
                    <div class="mt-4">
                      <app-state-panel title="{{ 'product.noReviews' | t }}" message="{{ 'product.noReviewsMessage' | t }}" />
                    </div>
                  } @else {
                    <div class="mt-4 grid gap-3">
                      @for (review of reviews(); track review.id) {
                        <article class="rounded-ui border border-aurora-line bg-white p-4 dark:border-white/10 dark:bg-white/5">
                          <div class="flex flex-wrap items-center justify-between gap-3">
                            <div>
                              <p class="font-black text-aurora-ink dark:text-white">{{ review.authorName }}</p>
                              <p class="text-xs text-aurora-muted dark:text-stone-400">{{ review.createdAt | date:'mediumDate' }}</p>
                            </div>
                            <div class="flex items-center gap-1 text-aurora-pine dark:text-aurora-pinebright">
                              @for (star of stars; track star) {
                                <lucide-icon [img]="Star" size="15" [class.opacity-30]="star > review.rating" />
                              }
                            </div>
                          </div>
                          @if (review.title) {
                            <h3 class="mt-4 font-black text-aurora-ink dark:text-white">{{ review.title }}</h3>
                          }
                          @if (review.comment) {
                            <p class="mt-2 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ review.comment }}</p>
                          }
                        </article>
                      }
                    </div>
                  }
                </div>

                <div class="rounded-ui border border-aurora-line bg-white p-4 dark:border-white/10 dark:bg-white/5">
                  @if (!auth.isAuthenticated()) {
                    <app-state-panel title="{{ 'nav.signIn' | t }}" message="{{ 'product.loginToReview' | t }}" />
                    <a routerLink="/login" class="ui-button ui-button-primary mt-4 w-full">{{ 'nav.signIn' | t }}</a>
                  } @else {
                    <form [formGroup]="reviewForm" (ngSubmit)="submitReview(item)" class="space-y-4">
                      <label class="block">
                        <span class="text-sm font-black text-aurora-ink dark:text-white">{{ 'product.reviewRating' | t }}</span>
                        <div class="mt-2 flex gap-2">
                          @for (star of stars; track star) {
                            <button class="ui-button h-10 w-10 min-h-10 p-0" [class.ui-button-primary]="reviewForm.controls.rating.value >= star" [class.ui-button-secondary]="reviewForm.controls.rating.value < star" type="button" (click)="reviewForm.controls.rating.setValue(star)">
                              <lucide-icon [img]="Star" size="16" />
                            </button>
                          }
                        </div>
                      </label>
                      <label class="block">
                        <span class="text-sm font-black text-aurora-ink dark:text-white">{{ 'product.reviewTitle' | t }}</span>
                        <input class="ui-input mt-2" formControlName="title" maxlength="160" />
                      </label>
                      <label class="block">
                        <span class="text-sm font-black text-aurora-ink dark:text-white">{{ 'product.reviewComment' | t }}</span>
                        <textarea class="ui-input mt-2 h-auto min-h-28 py-3" formControlName="comment"></textarea>
                      </label>
                      @if (reviewMessage()) {
                        <p class="rounded-ui bg-aurora-pine/10 px-3 py-2 text-sm font-bold text-aurora-pine dark:bg-aurora-pine/15 dark:text-aurora-pinebright">{{ reviewMessage() }}</p>
                      }
                      @if (reviewError()) {
                        <p class="rounded-ui bg-aurora-rose/10 px-3 py-2 text-sm font-bold text-aurora-rose dark:bg-aurora-rose/15">{{ reviewError() }}</p>
                      }
                      <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="reviewForm.invalid || reviewSubmitting()">{{ 'product.submitReview' | t }}</button>
                    </form>
                  }
                </div>
              </div>
            }
          </div>
        </div>
      }
    </section>
  `
})
export class ProductDetailPageComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);

  readonly product = signal<Product | null>(null);
  readonly reviews = signal<Review[]>([]);
  readonly selectedImage = signal<string | null>(null);
  readonly selectedVariant = signal<string | null>(null);
  readonly activeTab = signal<ProductTab>('description');
  readonly loading = signal(true);
  readonly reviewsLoading = signal(false);
  readonly reviewsError = signal<string | null>(null);
  readonly cartLoading = signal(false);
  readonly wishlistLoading = signal(false);
  readonly reviewSubmitting = signal(false);
  readonly reviewMessage = signal<string | null>(null);
  readonly reviewError = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  readonly reviewForm = this.formBuilder.nonNullable.group({
    rating: [5, [Validators.required, Validators.min(1), Validators.max(5)]],
    title: ['', [Validators.maxLength(160)]],
    comment: ['']
  });

  readonly ArrowLeft = ArrowLeft;
  readonly BadgeCheck = BadgeCheck;
  readonly Heart = Heart;
  readonly ListChecks = ListChecks;
  readonly MessageSquareText = MessageSquareText;
  readonly ShoppingBag = ShoppingBag;
  readonly Star = Star;
  readonly Zap = Zap;
  readonly stars = [1, 2, 3, 4, 5];

  readonly promises = [
    { icon: Truck, title: 'product.promise.shipping', copy: 'product.promise.shippingCopy' },
    { icon: ShieldCheck, title: 'product.promise.secure', copy: 'product.promise.secureCopy' },
    { icon: BadgeCheck, title: 'product.promise.quality', copy: 'product.promise.qualityCopy' }
  ];

  constructor(
    readonly auth: AuthService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly cartService: CartService,
    private readonly catalogService: CatalogService,
    private readonly language: LanguageService,
    private readonly reviewService: ReviewService,
    private readonly toast: ToastService,
    readonly wishlist: WishlistService
  ) {}

  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      this.wishlist.loadWishlist().subscribe({ error: () => undefined });
    }

    // Subscribe (not snapshot) so navigating product -> product (back/forward,
    // links from cart/wishlist/orders) reloads and resets the view instead of
    // keeping the previous product, which Angular's route reuse would otherwise show.
    this.route.paramMap.subscribe((params) => this.loadProduct(params.get('slug')));
  }

  private loadProduct(slug: string | null): void {
    if (!slug) {
      this.error.set(this.language.translate('product.unavailable'));
      this.loading.set(false);
      return;
    }

    // Reset per-product view state before fetching the new one.
    this.product.set(null);
    this.reviews.set([]);
    this.selectedImage.set(null);
    this.selectedVariant.set(null);
    this.activeTab.set('description');
    this.error.set(null);
    this.loading.set(true);

    this.catalogService.getProduct(slug).subscribe({
      next: (product) => {
        this.product.set(product);
        this.selectedImage.set(this.imageUrl(product));
        this.selectedVariant.set(firstActiveVariantId(product));
        this.loading.set(false);
        this.loadReviews(product.id);
      },
      error: () => {
        this.error.set(this.language.translate('product.unavailable'));
        this.loading.set(false);
      }
    });
  }

  addToCart(product: Product): void {
    const variantId = this.selectedVariantId(product);
    if (!variantId) {
      this.toast.error(this.language.translate('cart.toast.unavailable'));
      return;
    }

    if (!this.auth.isAuthenticated()) {
      this.promptSignIn('cart.toast.signInRequired');
      return;
    }

    this.cartLoading.set(true);
    this.cartService.addItem({ variantId, quantity: 1 }).subscribe({
      next: () => {
        this.cartLoading.set(false);
        this.toast.success(this.language.translate('cart.toast.added'));
      },
      error: (err) => {
        this.cartLoading.set(false);
        this.toast.error(this.language.translate(cartErrorKey(err)));
      }
    });
  }

  buyNow(product: Product): void {
    const variantId = this.selectedVariantId(product);
    if (!variantId) {
      this.toast.error(this.language.translate('cart.toast.unavailable'));
      return;
    }

    if (!this.auth.isAuthenticated()) {
      this.promptSignIn('cart.toast.signInRequired');
      return;
    }

    this.cartLoading.set(true);
    this.cartService.addItem({ variantId, quantity: 1 }).subscribe({
      next: () => this.router.navigateByUrl('/checkout'),
      error: (err) => {
        this.cartLoading.set(false);
        this.toast.error(this.language.translate(cartErrorKey(err)));
      }
    });
  }

  toggleWishlist(product: Product): void {
    if (!this.auth.isAuthenticated()) {
      this.promptSignIn('wishlist.login');
      return;
    }

    const wasWishlisted = this.wishlist.isWishlisted(product.id);
    const request: Observable<unknown> = wasWishlisted
      ? this.wishlist.remove(product.id)
      : this.wishlist.add(product.id);

    this.wishlistLoading.set(true);
    request.subscribe({
      next: () => {
        this.wishlistLoading.set(false);
        this.toast.success(this.language.translate(wasWishlisted ? 'wishlist.toast.removed' : 'wishlist.toast.added'));
      },
      error: () => {
        this.wishlistLoading.set(false);
        this.toast.error(this.language.translate('wishlist.toast.error'));
      }
    });
  }

  private promptSignIn(messageKey: string): void {
    this.toast.info(this.language.translate(messageKey));
    this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
  }

  submitReview(product: Product): void {
    if (this.reviewForm.invalid) {
      this.reviewForm.markAllAsTouched();
      return;
    }

    this.reviewSubmitting.set(true);
    this.reviewMessage.set(null);
    this.reviewError.set(null);
    const value = this.reviewForm.getRawValue();

    this.reviewService.create(product.id, {
      rating: value.rating,
      title: value.title.trim() || null,
      comment: value.comment.trim() || null
    }).subscribe({
      next: (review) => {
        this.reviews.set([review, ...this.reviews()]);
        this.reviewForm.reset({ rating: 5, title: '', comment: '' });
        this.reviewMessage.set(this.language.translate('product.reviewSuccess'));
        this.reviewSubmitting.set(false);
      },
      error: () => {
        this.reviewError.set(this.language.translate('product.reviewError'));
        this.reviewSubmitting.set(false);
      }
    });
  }

  selectedVariantId(product: Product): string | null {
    return this.selectedVariant() ?? firstActiveVariantId(product);
  }

  /** Price of the currently selected variant so the headline matches the CTA;
   *  falls back to the product base price. */
  displayPrice(product: Product): number {
    const id = this.selectedVariant();
    const variant = id ? product.variants.find((v) => v.id === id) : undefined;
    return variant?.effectivePrice ?? product.basePrice;
  }

  averageRating(): string {
    if (this.reviews().length === 0) {
      return '0.0';
    }

    const total = this.reviews().reduce((sum, review) => sum + review.rating, 0);
    return (total / this.reviews().length).toFixed(1);
  }

  imageUrl(product: Product): string {
    return productImage(product);
  }

  imageAlt(product: Product): string {
    return productImageAlt(product);
  }

  galleryUrls(product: Product): string[] {
    return productGallery(product);
  }

  activeVariantCount(product: Product): number {
    return product.variants.filter((variant) => variant.active).length || 1;
  }

  private loadReviews(productId: string): void {
    this.reviewsLoading.set(true);
    this.reviewsError.set(null);
    this.reviewService.list(productId).subscribe({
      next: (reviews) => {
        this.reviews.set(reviews);
        this.reviewsLoading.set(false);
      },
      error: () => {
        this.reviewsError.set(this.language.translate('product.reviewsLoadError'));
        this.reviewsLoading.set(false);
      }
    });
  }
}
