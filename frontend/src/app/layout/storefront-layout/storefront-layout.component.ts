import { Component } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideAngularModule,
  ArrowRight,
  Heart,
  Menu,
  Moon,
  PackageCheck,
  Search,
  ShieldCheck,
  ShoppingBag,
  Sparkles,
  Sun,
  Truck,
  UserRound
} from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { AuthService } from '../../services/auth.service';
import { CartService } from '../../services/cart.service';
import { LanguageService } from '../../core/i18n/language.service';
import { ThemeService } from '../../core/theme/theme.service';
import { WishlistService } from '../../services/wishlist.service';
import { ToastHostComponent } from '../../shared/toast-host/toast-host.component';

@Component({
  selector: 'app-storefront-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule, TranslatePipe, ToastHostComponent],
  template: `
    <div class="min-h-screen text-aurora-ink dark:text-white">
      <a href="#main-content" class="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[60] focus:rounded-ui focus:bg-aurora-ink focus:px-4 focus:py-2 focus:text-sm focus:font-bold focus:text-white focus:shadow-lift dark:focus:bg-white dark:focus:text-aurora-night">{{ 'a11y.skipToContent' | t }}</a>
      <div class="border-b border-white/10 bg-aurora-night text-white">
        <div class="page-shell flex min-h-10 items-center justify-center gap-3 text-center text-xs font-bold sm:justify-between">
          <span class="inline-flex items-center gap-2">
            <lucide-icon class="text-amber-300" [img]="Sparkles" size="14" />
            {{ 'nav.promo' | t }}
          </span>
          <a routerLink="/catalog" class="hidden cursor-pointer items-center gap-1 text-amber-200 transition-colors duration-200 hover:text-white sm:inline-flex">
            {{ 'nav.browseArrivals' | t }}
            <lucide-icon [img]="ArrowRight" size="14" />
          </a>
        </div>
      </div>

      <header class="sticky top-0 z-40 border-b border-white/60 bg-white/80 backdrop-blur-2xl dark:border-white/10 dark:bg-aurora-night/80">
        <div class="page-shell flex min-h-20 items-center gap-3 py-3">
          <a routerLink="/" class="flex cursor-pointer items-center gap-3" [attr.aria-label]="'a11y.home' | t">
            <span class="flex h-11 w-11 items-center justify-center rounded-ui bg-aurora-ink text-base font-black text-white shadow-lift dark:bg-white dark:text-aurora-night">A</span>
            <span class="hidden leading-none sm:block">
              <span class="block text-sm font-black uppercase tracking-[0.22em] text-aurora-ink dark:text-white">Aurora</span>
              <span class="mt-1 block text-[11px] font-bold text-aurora-muted dark:text-stone-400">Marketplace</span>
            </span>
          </a>

          <nav class="hidden items-center gap-1 md:flex">
            <a routerLink="/" routerLinkActive="bg-stone-100 dark:bg-white/10" [routerLinkActiveOptions]="{ exact: true }" class="cursor-pointer rounded-ui px-3 py-2 text-sm font-bold text-aurora-muted transition-colors duration-200 hover:bg-stone-100 hover:text-aurora-ink dark:text-stone-300 dark:hover:bg-white/10 dark:hover:text-white">{{ 'nav.home' | t }}</a>
            <a routerLink="/catalog" routerLinkActive="bg-stone-100 dark:bg-white/10" class="cursor-pointer rounded-ui px-3 py-2 text-sm font-bold text-aurora-muted transition-colors duration-200 hover:bg-stone-100 hover:text-aurora-ink dark:text-stone-300 dark:hover:bg-white/10 dark:hover:text-white">{{ 'nav.catalog' | t }}</a>
            <a routerLink="/account/orders" routerLinkActive="bg-stone-100 dark:bg-white/10" class="cursor-pointer rounded-ui px-3 py-2 text-sm font-bold text-aurora-muted transition-colors duration-200 hover:bg-stone-100 hover:text-aurora-ink dark:text-stone-300 dark:hover:bg-white/10 dark:hover:text-white">{{ 'nav.orders' | t }}</a>
            @if (auth.isAdmin()) {
              <a routerLink="/admin" class="cursor-pointer rounded-ui px-3 py-2 text-sm font-bold text-aurora-muted transition-colors duration-200 hover:bg-stone-100 hover:text-aurora-ink dark:text-stone-300 dark:hover:bg-white/10 dark:hover:text-white">{{ 'nav.admin' | t }}</a>
            }
          </nav>

          <label class="ml-auto hidden h-12 min-w-0 flex-1 max-w-xl items-center gap-2 rounded-ui border border-aurora-line bg-white px-3 text-aurora-muted shadow-sm transition duration-200 focus-within:border-aurora-amber focus-within:ring-2 focus-within:ring-aurora-amber/20 lg:flex dark:border-white/10 dark:bg-white/10">
            <lucide-icon [img]="Search" size="18" />
            <input #deskSearch class="min-w-0 flex-1 bg-transparent text-sm text-aurora-ink outline-none placeholder:text-stone-400 dark:text-white" [placeholder]="'nav.search' | t" [attr.aria-label]="'nav.search' | t" (keyup.enter)="submitSearch(deskSearch.value)" />
            <span class="rounded-ui bg-stone-100 px-2 py-1 text-[11px] font-black text-aurora-muted dark:bg-white/10 dark:text-stone-300">/</span>
          </label>

          <div class="flex items-center gap-2">
            <button class="ui-button ui-button-secondary h-10 min-h-10 px-2 text-xs" type="button" (click)="language.toggle()" [attr.aria-label]="'a11y.changeLanguage' | t">
              {{ language.language().toUpperCase() }}
            </button>
            <button class="ui-button ui-button-secondary h-10 w-10 min-h-10 p-0" type="button" (click)="theme.toggle()" [attr.aria-label]="'a11y.toggleTheme' | t">
              <lucide-icon [img]="theme.isDark() ? Sun : Moon" size="18" />
            </button>
            <a routerLink="/catalog" class="ui-button ui-button-secondary h-10 w-10 min-h-10 p-0 md:hidden" [attr.aria-label]="'a11y.openMenu' | t">
              <lucide-icon [img]="Menu" size="18" />
            </a>
            <a routerLink="/wishlist" class="ui-button ui-button-secondary relative h-10 w-10 min-h-10 p-0" [attr.aria-label]="'nav.wishlist' | t">
              <lucide-icon [img]="Heart" size="18" />
              @if (wishlist.count() > 0) {
                <span class="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-aurora-emerald px-1 text-[10px] font-black text-white">{{ wishlist.count() }}</span>
              }
            </a>
            <a routerLink="/cart" class="ui-button ui-button-secondary relative h-10 w-10 min-h-10 p-0" [attr.aria-label]="'nav.cart' | t">
              <lucide-icon [img]="ShoppingBag" size="18" />
              @if (cart.itemCount() > 0) {
                <span class="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-aurora-amber px-1 text-[10px] font-black text-white">{{ cart.itemCount() }}</span>
              }
            </a>
            @if (auth.currentUser(); as user) {
              <button class="ui-button ui-button-primary hidden sm:inline-flex" type="button" (click)="auth.logout()">
                <lucide-icon [img]="UserRound" size="17" />
                {{ user.firstName }}
              </button>
            } @else {
              <a routerLink="/login" class="ui-button ui-button-primary hidden sm:inline-flex">
                <lucide-icon [img]="UserRound" size="17" />
                {{ 'nav.signIn' | t }}
              </a>
            }
          </div>
        </div>
        <div class="page-shell pb-3 lg:hidden">
          <label class="flex h-11 items-center gap-2 rounded-ui border border-aurora-line bg-white px-3 text-aurora-muted shadow-sm dark:border-white/10 dark:bg-white/10">
            <lucide-icon [img]="Search" size="17" />
            <input #mobSearch class="min-w-0 flex-1 bg-transparent text-sm text-aurora-ink outline-none placeholder:text-stone-400 dark:text-white" [placeholder]="'nav.mobileSearch' | t" [attr.aria-label]="'nav.mobileSearch' | t" (keyup.enter)="submitSearch(mobSearch.value)" />
          </label>
        </div>
      </header>

      <main id="main-content" tabindex="-1" class="outline-none">
        <router-outlet (activate)="focusMain()" />
      </main>

      <footer class="mt-20 border-t border-aurora-line bg-aurora-night text-white dark:border-white/10">
        <div class="page-shell py-12">
          <div class="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
            <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6 shadow-innerline">
              <p class="section-kicker">{{ 'footer.membership' | t }}</p>
              <h2 class="mt-3 max-w-2xl text-3xl font-black">{{ 'footer.title' | t }}</h2>
              <div class="mt-6 flex flex-col gap-3 sm:flex-row">
                <input class="ui-input border-white/10 bg-white/10 text-white placeholder:text-stone-400" [placeholder]="'footer.email' | t" [attr.aria-label]="'a11y.newsletterEmail' | t" />
                <button class="ui-button bg-white text-aurora-night hover:bg-stone-100" type="button">{{ 'footer.notify' | t }}</button>
              </div>
            </div>
            <div class="grid gap-3 sm:grid-cols-3 lg:grid-cols-1">
              @for (item of trustItems; track item.title) {
                <div class="rounded-ui border border-white/10 bg-white/[0.06] p-4">
                  <lucide-icon class="text-amber-300" [img]="item.icon" size="20" />
                  <p class="mt-3 font-black">{{ item.title | t }}</p>
                  <p class="mt-1 text-sm leading-5 text-stone-300">{{ item.copy | t }}</p>
                </div>
              }
            </div>
          </div>

          <div class="mt-10 grid gap-8 border-t border-white/10 pt-10 md:grid-cols-[1.4fr_1fr_1fr_1fr]">
            <div>
              <p class="text-xl font-black">Aurora Marketplace</p>
              <p class="mt-4 max-w-sm text-sm leading-6 text-stone-300">
                {{ 'footer.copy' | t }}
              </p>
            </div>
            <div>
              <p class="font-bold">{{ 'footer.shop' | t }}</p>
              <div class="mt-4 grid gap-3 text-sm text-stone-300">
                <a routerLink="/catalog" class="cursor-pointer hover:text-white">{{ 'nav.catalog' | t }}</a>
                <a routerLink="/" class="cursor-pointer hover:text-white">{{ 'home.featured' | t }}</a>
                <a routerLink="/cart" class="cursor-pointer hover:text-white">{{ 'nav.cart' | t }}</a>
              </div>
            </div>
            <div>
              <p class="font-bold">{{ 'footer.account' | t }}</p>
              <div class="mt-4 grid gap-3 text-sm text-stone-300">
                <a routerLink="/login" class="cursor-pointer hover:text-white">{{ 'nav.signIn' | t }}</a>
                <a routerLink="/register" class="cursor-pointer hover:text-white">{{ 'auth.create' | t }}</a>
                <a routerLink="/account/orders" class="cursor-pointer hover:text-white">{{ 'nav.orders' | t }}</a>
              </div>
            </div>
            <div>
              <p class="font-bold">{{ 'footer.operations' | t }}</p>
              <p class="mt-4 text-sm leading-6 text-stone-300">{{ 'footer.help' | t }}</p>
            </div>
          </div>
        </div>
      </footer>

      <app-toast-host />
    </div>
  `
})
export class StorefrontLayoutComponent {
  readonly ArrowRight = ArrowRight;
  readonly Heart = Heart;
  readonly Menu = Menu;
  readonly Moon = Moon;
  readonly Search = Search;
  readonly ShoppingBag = ShoppingBag;
  readonly Sparkles = Sparkles;
  readonly Sun = Sun;
  readonly UserRound = UserRound;

  readonly trustItems = [
    { icon: ShieldCheck, title: 'footer.trust.secure', copy: 'footer.trust.secureCopy' },
    { icon: Truck, title: 'footer.trust.stock', copy: 'footer.trust.stockCopy' },
    { icon: PackageCheck, title: 'footer.trust.tracking', copy: 'footer.trust.trackingCopy' }
  ];

  constructor(
    readonly auth: AuthService,
    readonly cart: CartService,
    readonly language: LanguageService,
    readonly theme: ThemeService,
    readonly wishlist: WishlistService,
    private readonly router: Router
  ) {}

  submitSearch(term: string): void {
    const value = (term ?? '').trim();
    this.router.navigate(['/catalog'], {
      queryParams: value ? { q: value } : {}
    });
  }

  /** Move focus to the page content on navigation so keyboard / screen-reader users
   *  start at the new page instead of the stale header. preventScroll lets the
   *  router's own scroll restoration handle position. */
  private routeActivated = false;

  focusMain(): void {
    // Don't steal focus on first paint — leave it at the top so the skip link is
    // the first tab stop. On later SPA navigations, move focus to the page content
    // so keyboard / screen-reader users start at the new page, not the stale header.
    if (!this.routeActivated) {
      this.routeActivated = true;
      return;
    }
    if (typeof document === 'undefined') {
      return;
    }
    document.getElementById('main-content')?.focus({ preventScroll: true });
  }
}
