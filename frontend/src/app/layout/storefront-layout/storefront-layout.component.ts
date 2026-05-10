import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideAngularModule, Heart, Menu, Search, ShoppingCart, UserRound } from 'lucide-angular';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-storefront-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule],
  template: `
    <div class="min-h-screen text-slate-950 dark:text-white">
      <div class="bg-slate-950 text-white">
        <div class="page-shell flex min-h-10 items-center justify-between gap-4 text-xs font-semibold">
          <span>Free simulated checkout on portfolio orders over $75</span>
          <a routerLink="/catalog" class="hidden cursor-pointer text-emerald-300 hover:text-white sm:inline">Explore launch offers</a>
        </div>
      </div>

      <header class="sticky top-0 z-40 border-b border-white/60 bg-white/80 backdrop-blur-xl dark:border-white/10 dark:bg-slate-950/80">
        <div class="page-shell flex h-20 items-center gap-4">
          <a routerLink="/" class="flex cursor-pointer items-center gap-3" aria-label="Aurora Marketplace home">
            <span class="flex h-10 w-10 items-center justify-center rounded-ui bg-slate-950 text-sm font-black text-white dark:bg-white dark:text-slate-950">A</span>
            <span class="hidden text-sm font-black uppercase tracking-[0.22em] text-slate-950 sm:block dark:text-white">Aurora</span>
          </a>

          <nav class="hidden items-center gap-1 md:flex">
            <a routerLink="/" routerLinkActive="bg-slate-100 dark:bg-white/10" [routerLinkActiveOptions]="{ exact: true }" class="cursor-pointer rounded-ui px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-white/10">Home</a>
            <a routerLink="/catalog" routerLinkActive="bg-slate-100 dark:bg-white/10" class="cursor-pointer rounded-ui px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-white/10">Catalog</a>
            <a routerLink="/admin" class="cursor-pointer rounded-ui px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-white/10">Admin</a>
          </nav>

          <label class="ml-auto hidden h-11 min-w-0 flex-1 max-w-xl items-center gap-2 rounded-ui border border-slate-200 bg-white px-3 text-slate-500 shadow-sm focus-within:border-aurora-ocean focus-within:ring-2 focus-within:ring-aurora-ocean/20 lg:flex dark:border-white/10 dark:bg-white/10">
            <lucide-icon [img]="Search" size="18" />
            <input class="min-w-0 flex-1 bg-transparent text-sm text-slate-950 outline-none placeholder:text-slate-400 dark:text-white" placeholder="Search products, brands, categories" />
          </label>

          <div class="flex items-center gap-2">
            <a routerLink="/catalog" class="ui-button ui-button-secondary h-10 w-10 p-0 md:hidden" aria-label="Open catalog">
              <lucide-icon [img]="Menu" size="18" />
            </a>
            <button class="ui-button ui-button-secondary h-10 w-10 p-0" type="button" aria-label="Wishlist">
              <lucide-icon [img]="Heart" size="18" />
            </button>
            <button class="ui-button ui-button-secondary h-10 w-10 p-0" type="button" aria-label="Cart">
              <lucide-icon [img]="ShoppingCart" size="18" />
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
      </header>

      <main>
        <router-outlet />
      </main>

      <footer class="mt-20 border-t border-slate-200 bg-slate-950 text-white dark:border-white/10">
        <div class="page-shell grid gap-10 py-12 md:grid-cols-[1.3fr_1fr_1fr_1fr]">
          <div>
            <p class="text-xl font-black">Aurora Marketplace</p>
            <p class="mt-4 max-w-sm text-sm leading-6 text-slate-300">
              A professional e-commerce portfolio with secure backend flows, premium storefront structure and admin-ready operations.
            </p>
          </div>
          <div>
            <p class="font-semibold">Shop</p>
            <div class="mt-4 grid gap-3 text-sm text-slate-300">
              <a routerLink="/catalog" class="cursor-pointer hover:text-white">Catalog</a>
              <a routerLink="/" class="cursor-pointer hover:text-white">Featured</a>
              <a routerLink="/" class="cursor-pointer hover:text-white">Offers</a>
            </div>
          </div>
          <div>
            <p class="font-semibold">Platform</p>
            <div class="mt-4 grid gap-3 text-sm text-slate-300">
              <a routerLink="/admin" class="cursor-pointer hover:text-white">Admin dashboard</a>
              <a routerLink="/login" class="cursor-pointer hover:text-white">Customer login</a>
              <a routerLink="/register" class="cursor-pointer hover:text-white">Create account</a>
            </div>
          </div>
          <div>
            <p class="font-semibold">Trust</p>
            <p class="mt-4 text-sm leading-6 text-slate-300">JWT auth, audit logs, inventory controls and simulated payments prepared for production hardening.</p>
          </div>
        </div>
      </footer>
    </div>
  `
})
export class StorefrontLayoutComponent {
  readonly Search = Search;
  readonly Heart = Heart;
  readonly ShoppingCart = ShoppingCart;
  readonly UserRound = UserRound;
  readonly Menu = Menu;

  constructor(readonly auth: AuthService) {}
}
