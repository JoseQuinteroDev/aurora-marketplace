import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideAngularModule,
  ArrowRight,
  Heart,
  Menu,
  PackageCheck,
  Search,
  ShieldCheck,
  ShoppingBag,
  Sparkles,
  Truck,
  UserRound
} from 'lucide-angular';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-storefront-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule],
  template: `
    <div class="min-h-screen text-aurora-ink dark:text-white">
      <div class="border-b border-white/10 bg-aurora-night text-white">
        <div class="page-shell flex min-h-10 items-center justify-center gap-3 text-center text-xs font-bold sm:justify-between">
          <span class="inline-flex items-center gap-2">
            <lucide-icon class="text-amber-300" [img]="Sparkles" size="14" />
            Spring edit: curated launches, secure checkout, live inventory.
          </span>
          <a routerLink="/catalog" class="hidden cursor-pointer items-center gap-1 text-amber-200 transition-colors duration-200 hover:text-white sm:inline-flex">
            Browse arrivals
            <lucide-icon [img]="ArrowRight" size="14" />
          </a>
        </div>
      </div>

      <header class="sticky top-0 z-40 border-b border-white/60 bg-white/80 backdrop-blur-2xl dark:border-white/10 dark:bg-aurora-night/80">
        <div class="page-shell flex min-h-20 items-center gap-3 py-3">
          <a routerLink="/" class="flex cursor-pointer items-center gap-3" aria-label="Aurora Marketplace home">
            <span class="flex h-11 w-11 items-center justify-center rounded-ui bg-aurora-ink text-base font-black text-white shadow-lift dark:bg-white dark:text-aurora-night">A</span>
            <span class="hidden leading-none sm:block">
              <span class="block text-sm font-black uppercase tracking-[0.22em] text-aurora-ink dark:text-white">Aurora</span>
              <span class="mt-1 block text-[11px] font-bold text-aurora-muted dark:text-stone-400">Marketplace</span>
            </span>
          </a>

          <nav class="hidden items-center gap-1 md:flex">
            <a routerLink="/" routerLinkActive="bg-stone-100 dark:bg-white/10" [routerLinkActiveOptions]="{ exact: true }" class="cursor-pointer rounded-ui px-3 py-2 text-sm font-bold text-aurora-muted transition-colors duration-200 hover:bg-stone-100 hover:text-aurora-ink dark:text-stone-300 dark:hover:bg-white/10 dark:hover:text-white">Home</a>
            <a routerLink="/catalog" routerLinkActive="bg-stone-100 dark:bg-white/10" class="cursor-pointer rounded-ui px-3 py-2 text-sm font-bold text-aurora-muted transition-colors duration-200 hover:bg-stone-100 hover:text-aurora-ink dark:text-stone-300 dark:hover:bg-white/10 dark:hover:text-white">Catalog</a>
            <a routerLink="/admin" class="cursor-pointer rounded-ui px-3 py-2 text-sm font-bold text-aurora-muted transition-colors duration-200 hover:bg-stone-100 hover:text-aurora-ink dark:text-stone-300 dark:hover:bg-white/10 dark:hover:text-white">Admin</a>
          </nav>

          <label class="ml-auto hidden h-12 min-w-0 flex-1 max-w-xl items-center gap-2 rounded-ui border border-aurora-line bg-white px-3 text-aurora-muted shadow-sm transition duration-200 focus-within:border-aurora-amber focus-within:ring-2 focus-within:ring-aurora-amber/20 lg:flex dark:border-white/10 dark:bg-white/10">
            <lucide-icon [img]="Search" size="18" />
            <input class="min-w-0 flex-1 bg-transparent text-sm text-aurora-ink outline-none placeholder:text-stone-400 dark:text-white" placeholder="Search products, brands, categories" />
            <span class="rounded-ui bg-stone-100 px-2 py-1 text-[11px] font-black text-aurora-muted dark:bg-white/10 dark:text-stone-300">/</span>
          </label>

          <div class="flex items-center gap-2">
            <a routerLink="/catalog" class="ui-button ui-button-secondary h-10 w-10 min-h-10 p-0 md:hidden" aria-label="Open catalog">
              <lucide-icon [img]="Menu" size="18" />
            </a>
            <button class="ui-button ui-button-secondary h-10 w-10 min-h-10 p-0" type="button" aria-label="Wishlist">
              <lucide-icon [img]="Heart" size="18" />
            </button>
            <button class="ui-button ui-button-secondary relative h-10 w-10 min-h-10 p-0" type="button" aria-label="Cart">
              <lucide-icon [img]="ShoppingBag" size="18" />
              <span class="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-aurora-amber px-1 text-[10px] font-black text-white">0</span>
            </button>
            @if (auth.currentUser(); as user) {
              <button class="ui-button ui-button-primary hidden sm:inline-flex" type="button" (click)="auth.logout()">
                <lucide-icon [img]="UserRound" size="17" />
                {{ user.firstName }}
              </button>
            } @else {
              <a routerLink="/login" class="ui-button ui-button-primary hidden sm:inline-flex">
                <lucide-icon [img]="UserRound" size="17" />
                Sign in
              </a>
            }
          </div>
        </div>
        <div class="page-shell pb-3 lg:hidden">
          <label class="flex h-11 items-center gap-2 rounded-ui border border-aurora-line bg-white px-3 text-aurora-muted shadow-sm dark:border-white/10 dark:bg-white/10">
            <lucide-icon [img]="Search" size="17" />
            <input class="min-w-0 flex-1 bg-transparent text-sm text-aurora-ink outline-none placeholder:text-stone-400 dark:text-white" placeholder="Search Aurora" />
          </label>
        </div>
      </header>

      <main>
        <router-outlet />
      </main>

      <footer class="mt-20 border-t border-aurora-line bg-aurora-night text-white dark:border-white/10">
        <div class="page-shell py-12">
          <div class="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
            <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6 shadow-innerline">
              <p class="section-kicker">Aurora membership</p>
              <h2 class="mt-3 max-w-2xl text-3xl font-black">Private drops, sharper curation, checkout that feels calm.</h2>
              <div class="mt-6 flex flex-col gap-3 sm:flex-row">
                <input class="ui-input border-white/10 bg-white/10 text-white placeholder:text-stone-400" placeholder="email@aurora.dev" aria-label="Newsletter email" />
                <button class="ui-button bg-white text-aurora-night hover:bg-stone-100" type="button">Notify me</button>
              </div>
            </div>
            <div class="grid gap-3 sm:grid-cols-3 lg:grid-cols-1">
              @for (item of trustItems; track item.title) {
                <div class="rounded-ui border border-white/10 bg-white/[0.06] p-4">
                  <lucide-icon class="text-amber-300" [img]="item.icon" size="20" />
                  <p class="mt-3 font-black">{{ item.title }}</p>
                  <p class="mt-1 text-sm leading-5 text-stone-300">{{ item.copy }}</p>
                </div>
              }
            </div>
          </div>

          <div class="mt-10 grid gap-8 border-t border-white/10 pt-10 md:grid-cols-[1.4fr_1fr_1fr_1fr]">
            <div>
              <p class="text-xl font-black">Aurora Marketplace</p>
              <p class="mt-4 max-w-sm text-sm leading-6 text-stone-300">
                Premium storefront for a full-stack commerce portfolio with secure customer flows and admin operations.
              </p>
            </div>
            <div>
              <p class="font-bold">Shop</p>
              <div class="mt-4 grid gap-3 text-sm text-stone-300">
                <a routerLink="/catalog" class="cursor-pointer hover:text-white">Catalog</a>
                <a routerLink="/" class="cursor-pointer hover:text-white">Featured</a>
                <a routerLink="/" class="cursor-pointer hover:text-white">Offers</a>
              </div>
            </div>
            <div>
              <p class="font-bold">Account</p>
              <div class="mt-4 grid gap-3 text-sm text-stone-300">
                <a routerLink="/login" class="cursor-pointer hover:text-white">Sign in</a>
                <a routerLink="/register" class="cursor-pointer hover:text-white">Create account</a>
                <a routerLink="/admin" class="cursor-pointer hover:text-white">Admin</a>
              </div>
            </div>
            <div>
              <p class="font-bold">Operations</p>
              <p class="mt-4 text-sm leading-6 text-stone-300">Inventory, audit logs, batch jobs and simulated payments are ready for the next frontend layer.</p>
            </div>
          </div>
        </div>
      </footer>
    </div>
  `
})
export class StorefrontLayoutComponent {
  readonly ArrowRight = ArrowRight;
  readonly Heart = Heart;
  readonly Menu = Menu;
  readonly Search = Search;
  readonly ShoppingBag = ShoppingBag;
  readonly Sparkles = Sparkles;
  readonly UserRound = UserRound;

  readonly trustItems = [
    { icon: ShieldCheck, title: 'Protected flows', copy: 'JWT-ready access, clean states and role-aware routing.' },
    { icon: Truck, title: 'Inventory aware', copy: 'Stock and product truth stay owned by the backend.' },
    { icon: PackageCheck, title: 'Order ready', copy: 'Cart, checkout and simulated payments are API-aligned.' }
  ];

  constructor(readonly auth: AuthService) {}
}
