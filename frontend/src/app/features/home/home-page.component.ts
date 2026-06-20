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
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
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
    TranslatePipe,
    ProductCardComponent,
    SectionHeaderComponent,
    SkeletonProductCardComponent,
    StatePanelComponent
  ],
  template: `
    <section class="hero-editorial overflow-hidden">
      <div class="page-shell relative z-10 py-16 lg:py-24">
        <div class="hero-reveal flex items-center gap-3 text-xs font-black uppercase tracking-[0.22em] text-aurora-gold dark:text-amber-300" style="animation-delay: 60ms">
          <span class="hero-rule"></span>
          <lucide-icon [img]="Sparkles" size="14" />
          {{ 'home.badge' | t }}
        </div>

        <h1 class="hero-reveal mt-6 max-w-[15ch] text-[clamp(2.75rem,8.5vw,7rem)] font-semibold leading-[0.9] tracking-tight text-aurora-ink dark:text-white" style="animation-delay: 140ms">
          {{ 'home.title' | t }}
        </h1>

        <div class="mt-12 grid items-end gap-10 lg:grid-cols-[1fr_0.82fr]">
          <div class="hero-reveal max-w-xl" style="animation-delay: 240ms">
            <p class="text-base leading-8 text-aurora-muted sm:text-lg dark:text-stone-300">{{ 'home.subtitle' | t }}</p>
            <div class="mt-8 flex flex-col gap-3 sm:flex-row">
              <a routerLink="/catalog" class="ui-button ui-button-primary">
                {{ 'home.cta' | t }}
                <lucide-icon [img]="ArrowRight" size="18" />
              </a>
              <a routerLink="/register" class="ui-button ui-button-secondary">{{ 'home.account' | t }}</a>
            </div>
          </div>

          <div class="hero-reveal relative" style="animation-delay: 340ms">
            <div class="overflow-hidden rounded-soft border border-aurora-line/70 shadow-premium dark:border-white/10">
              <img
                class="h-[340px] w-full object-cover sm:h-[440px]"
                src="https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=1200&q=85"
                alt="Escritorio con portátil y auriculares"
              />
            </div>
            <div class="absolute -left-3 bottom-5 flex items-center gap-2 rounded-ui border border-aurora-line bg-aurora-paper px-3 py-2 shadow-lift dark:border-white/10 dark:bg-aurora-night">
              <span class="flex h-7 w-7 items-center justify-center rounded-full bg-emerald-50 text-aurora-emerald dark:bg-emerald-500/15">
                <lucide-icon [img]="CheckCircle2" size="15" />
              </span>
              <span class="text-xs font-bold text-aurora-ink dark:text-white">{{ 'home.hero.inStock' | t }}</span>
            </div>
          </div>
        </div>

        <div class="hero-reveal mt-14 flex flex-wrap items-center gap-x-10 gap-y-4 border-t border-aurora-line/70 pt-6 dark:border-white/10" style="animation-delay: 440ms">
          @for (metric of heroMetrics; track metric.labelKey) {
            <div class="flex items-baseline gap-2">
              <span class="font-display text-2xl font-semibold text-aurora-ink dark:text-white">{{ metric.valueKey | t }}</span>
              <span class="text-sm font-semibold text-aurora-muted dark:text-stone-300">{{ metric.labelKey | t }}</span>
            </div>
          }
        </div>
      </div>
    </section>

    <section class="page-shell py-14">
      <app-section-header eyebrow="{{ 'home.categories.eyebrow' | t }}" title="{{ 'home.categories' | t }}" description="{{ 'home.categories.description' | t }}">
        <a routerLink="/catalog" class="ui-button ui-button-secondary">{{ 'home.openCatalog' | t }}</a>
      </app-section-header>

      <div class="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        @for (category of categories; track category.name) {
          <a [routerLink]="['/catalog']" [queryParams]="{ category: category.slug }" class="soft-card group cursor-pointer overflow-hidden">
            <div class="relative h-48 overflow-hidden">
              <img class="h-full w-full object-cover transition duration-500 group-hover:scale-[1.04] motion-reduce:transition-none motion-reduce:group-hover:scale-100" [src]="category.image" [alt]="category.name" />
              <div class="absolute inset-0 bg-gradient-to-t from-aurora-night/70 via-aurora-night/10 to-transparent"></div>
              <div class="absolute bottom-4 left-4 right-4">
                <p class="text-xl font-black text-white">{{ category.name | t }}</p>
                <p class="mt-1 text-sm font-medium text-stone-200">{{ category.copy | t }}</p>
              </div>
            </div>
          </a>
        }
      </div>
    </section>

    <section class="border-y border-aurora-line/80 bg-white/70 py-14 dark:border-white/10 dark:bg-white/[0.03]">
      <div class="page-shell">
        <app-section-header eyebrow="{{ 'home.featured.eyebrow' | t }}" title="{{ 'home.featured' | t }}" description="{{ 'home.featured.description' | t }}">
          <a routerLink="/catalog" class="ui-button ui-button-primary">{{ 'home.openCatalog' | t }}</a>
        </app-section-header>

        @if (loading()) {
          <div class="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            @for (item of skeletonItems; track item) {
              <app-skeleton-product-card />
            }
          </div>
        } @else if (error()) {
          <div class="mt-8">
            <app-state-panel mode="error" title="{{ 'common.error' | t }}" [message]="error()!" />
          </div>
        } @else if (featuredProducts().length === 0) {
          <div class="mt-8">
            <app-state-panel title="{{ 'home.empty' | t }}" message="{{ 'home.emptyMessage' | t }}" />
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
          <img class="absolute inset-0 h-full w-full object-cover opacity-35" src="https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1400&q=80" alt="Travel and lifestyle products" />
          <div class="relative z-10 max-w-xl">
            <p class="section-kicker">{{ 'home.promo.eyebrow' | t }}</p>
            <h2 class="mt-3 text-4xl font-black leading-tight">{{ 'home.promo.title' | t }}</h2>
            <p class="mt-4 text-sm leading-6 text-stone-200">{{ 'home.promo.copy' | t }}</p>
            <a routerLink="/catalog" class="ui-button mt-7 bg-white text-aurora-night hover:bg-stone-100">
              {{ 'home.promo.cta' | t }}
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
                  <h3 class="font-black text-aurora-ink dark:text-white">{{ benefit.title | t }}</h3>
                  <p class="mt-1 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ benefit.copy | t }}</p>
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
            <h3 class="mt-5 text-lg font-black text-aurora-ink dark:text-white">{{ promise.title | t }}</h3>
            <p class="mt-2 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ promise.copy | t }}</p>
          </div>
        }
      </div>
    </section>

    <section class="page-shell py-14">
      <div class="overflow-hidden rounded-ui border border-aurora-line bg-white shadow-premium dark:border-white/10 dark:bg-white/[0.07]">
        <div class="grid items-center gap-0 lg:grid-cols-[0.9fr_1.1fr]">
          <img class="h-72 w-full object-cover lg:h-full" src="https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=1400&q=85" alt="Surtido de productos de tecnología y estilo de vida" />
          <div class="p-7 sm:p-10">
            <p class="section-kicker">{{ 'home.club.eyebrow' | t }}</p>
            <h2 class="mt-3 text-3xl font-black text-aurora-ink sm:text-4xl dark:text-white">{{ 'home.club.title' | t }}</h2>
            <p class="mt-4 max-w-xl text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'home.club.copy' | t }}</p>
            <div class="mt-7 flex flex-col gap-3 sm:flex-row">
              <a routerLink="/register" class="ui-button ui-button-primary">
                {{ 'home.club.join' | t }}
                <lucide-icon [img]="ArrowRight" size="18" />
              </a>
              <a routerLink="/login" class="ui-button ui-button-secondary">{{ 'nav.signIn' | t }}</a>
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
    { valueKey: 'home.metric.shippingValue', labelKey: 'home.metric.shippingLabel' },
    { valueKey: 'home.metric.returnsValue', labelKey: 'home.metric.returnsLabel' },
    { valueKey: 'home.metric.secureValue', labelKey: 'home.metric.secureLabel' }
  ];
  readonly categories = [
    { name: 'home.cat.tech', copy: 'home.cat.techCopy', slug: 'computing', image: 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=900&q=80' },
    { name: 'home.cat.audio', copy: 'home.cat.audioCopy', slug: 'audio', image: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80' },
    { name: 'home.cat.home', copy: 'home.cat.homeCopy', slug: 'smart-home', image: 'https://images.unsplash.com/photo-1519710164239-da123dc03ef4?auto=format&fit=crop&w=900&q=80' },
    { name: 'home.cat.travel', copy: 'home.cat.travelCopy', slug: 'travel', image: 'https://images.unsplash.com/photo-1553531384-cc64ac80f931?auto=format&fit=crop&w=900&q=80' }
  ];
  readonly benefits = [
    { icon: Truck, title: 'home.benefit.shipping', copy: 'home.benefit.shippingCopy' },
    { icon: CreditCard, title: 'home.benefit.payment', copy: 'home.benefit.paymentCopy' },
    { icon: Headphones, title: 'home.benefit.support', copy: 'home.benefit.supportCopy' },
    { icon: RotateCcw, title: 'home.benefit.returns', copy: 'home.benefit.returnsCopy' }
  ];
  readonly promises = [
    { icon: ShoppingBag, title: 'home.promise.browse', copy: 'home.promise.browseCopy' },
    { icon: ShieldCheck, title: 'home.promise.confidence', copy: 'home.promise.confidenceCopy' },
    { icon: Zap, title: 'home.promise.together', copy: 'home.promise.togetherCopy' }
  ];

  constructor(
    private readonly catalogService: CatalogService,
    private readonly language: LanguageService
  ) {}

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
        this.error.set(this.language.translate('home.loadError'));
        this.loading.set(false);
      }
    });
  }
}
