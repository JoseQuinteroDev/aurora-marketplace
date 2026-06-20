import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { LucideAngularModule, Boxes, ClipboardList, LayoutDashboard, LogOut, Package, ShieldCheck } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
import { AuthService } from '../../services/auth.service';
import { ToastHostComponent } from '../../shared/toast-host/toast-host.component';

@Component({
  selector: 'app-admin-shell',
  imports: [RouterOutlet, RouterLink, LucideAngularModule, TranslatePipe, ToastHostComponent],
  template: `
    <div class="min-h-screen bg-slate-950 text-white">
      <aside class="fixed inset-y-0 left-0 hidden w-72 border-r border-white/10 bg-white/[0.04] p-5 backdrop-blur-xl lg:block">
        <a routerLink="/" class="flex cursor-pointer items-center gap-3">
          <span class="flex h-10 w-10 items-center justify-center rounded-ui bg-white text-sm font-black text-slate-950">A</span>
          <span class="font-black uppercase tracking-[0.22em]">Aurora</span>
        </a>
        <nav class="mt-10 grid gap-2">
          <a routerLink="/admin" class="flex cursor-pointer items-center gap-3 rounded-ui bg-white/10 px-3 py-3 text-sm font-semibold">
            <lucide-icon [img]="LayoutDashboard" size="18" />
            {{ 'admin.nav.dashboard' | t }}
          </a>
          <span class="flex items-center gap-3 rounded-ui px-3 py-3 text-sm font-semibold text-slate-400">
            <lucide-icon [img]="Package" size="18" />
            {{ 'nav.catalog' | t }}
          </span>
          <span class="flex items-center gap-3 rounded-ui px-3 py-3 text-sm font-semibold text-slate-400">
            <lucide-icon [img]="Boxes" size="18" />
            {{ 'admin.nav.inventory' | t }}
          </span>
          <span class="flex items-center gap-3 rounded-ui px-3 py-3 text-sm font-semibold text-slate-400">
            <lucide-icon [img]="ClipboardList" size="18" />
            {{ 'nav.orders' | t }}
          </span>
        </nav>
        <button class="ui-button mt-8 w-full border border-white/10 bg-white/10 text-white hover:bg-white/15" type="button" (click)="auth.logout()">
          <lucide-icon [img]="LogOut" size="17" />
          {{ 'nav.signOut' | t }}
        </button>
      </aside>

      <div class="lg:pl-72">
        <header class="sticky top-0 z-30 border-b border-white/10 bg-slate-950/90 backdrop-blur-xl">
          <div class="flex h-16 items-center justify-between px-4 sm:px-6 lg:px-8">
            <div>
              <p class="text-xs font-bold uppercase tracking-[0.18em] text-emerald-300">{{ 'admin.workspace' | t }}</p>
              <p class="text-sm text-slate-300">{{ 'admin.workspaceCopy' | t }}</p>
            </div>
            <div class="flex items-center gap-2 rounded-ui border border-white/10 bg-white/10 px-3 py-2 text-sm">
              <button class="cursor-pointer font-black text-amber-200" type="button" (click)="language.toggle()">{{ language.language().toUpperCase() }}</button>
              <lucide-icon [img]="ShieldCheck" size="17" />
              {{ 'admin.roleBadge' | t }}
            </div>
          </div>
        </header>
        <router-outlet />
      </div>

      <app-toast-host />
    </div>
  `
})
export class AdminShellComponent {
  readonly LayoutDashboard = LayoutDashboard;
  readonly Package = Package;
  readonly Boxes = Boxes;
  readonly ClipboardList = ClipboardList;
  readonly LogOut = LogOut;
  readonly ShieldCheck = ShieldCheck;

  constructor(
    readonly auth: AuthService,
    readonly language: LanguageService
  ) {}
}
