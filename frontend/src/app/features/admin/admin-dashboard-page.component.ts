import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { LucideAngularModule, BadgeDollarSign, Boxes, Package, ReceiptText, Star, Users } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LanguageService } from '../../core/i18n/language.service';
import { AdminDashboardSummary } from '../../core/models/admin.model';
import { AdminDashboardService } from '../../services/admin-dashboard.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-admin-dashboard-page',
  imports: [CurrencyPipe, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="px-4 py-8 sm:px-6 lg:px-8">
      <div class="max-w-7xl">
        <p class="text-xs font-bold uppercase tracking-[0.18em] text-emerald-300">{{ 'admin.eyebrow' | t }}</p>
        <h1 class="mt-3 text-3xl font-black tracking-normal text-white sm:text-4xl">{{ 'admin.title' | t }}</h1>
        <p class="mt-3 max-w-2xl text-sm leading-6 text-slate-300">{{ 'admin.subtitle' | t }}</p>

        @if (loading()) {
          <div class="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            @for (item of [1, 2, 3, 4, 5, 6, 7, 8]; track item) {
              <div class="h-32 animate-pulse rounded-ui bg-white/10"></div>
            }
          </div>
        } @else if (error()) {
          <div class="mt-8">
            <app-state-panel mode="error" title="{{ 'admin.unavailable' | t }}" [message]="error()!" />
          </div>
        } @else if (summary(); as data) {
          <div class="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            @for (metric of metrics(data); track metric.label) {
              <div class="rounded-ui border border-white/10 bg-white/[0.06] p-5">
                <div class="flex items-center justify-between gap-4">
                  <p class="text-sm font-semibold text-slate-300">{{ metric.label | t }}</p>
                  <span class="flex h-10 w-10 items-center justify-center rounded-ui bg-white/10 text-emerald-300">
                    <lucide-icon [img]="metric.icon" size="19" />
                  </span>
                </div>
                <p class="mt-5 text-3xl font-black text-white">{{ metric.value }}</p>
              </div>
            }
          </div>

          <div class="mt-8 grid gap-6 lg:grid-cols-[1fr_0.8fr]">
            <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6">
              <h2 class="text-lg font-bold text-white">{{ 'admin.ordersByStatus' | t }}</h2>
              <div class="mt-5 grid gap-3">
                @for (status of orderStatusRows(data); track status.name) {
                  <div class="flex items-center justify-between rounded-ui bg-white/[0.08] px-4 py-3 text-sm">
                    <span class="font-semibold text-slate-300">{{ ('order.status.' + status.name) | t }}</span>
                    <span class="font-black text-white">{{ status.count }}</span>
                  </div>
                } @empty {
                  <p class="text-sm text-slate-400">{{ 'admin.noOrders' | t }}</p>
                }
              </div>
            </div>

            <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6">
              <h2 class="text-lg font-bold text-white">{{ 'admin.paidRevenue' | t }}</h2>
              <p class="mt-5 text-4xl font-black text-emerald-300">{{ data.totalRevenuePaid | currency }}</p>
              <p class="mt-3 text-sm leading-6 text-slate-300">{{ 'admin.paidRevenueCopy' | t }}</p>
            </div>
          </div>
        }
      </div>
    </section>
  `
})
export class AdminDashboardPageComponent implements OnInit {
  readonly summary = signal<AdminDashboardSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor(
    private readonly adminDashboardService: AdminDashboardService,
    private readonly language: LanguageService
  ) {}

  ngOnInit(): void {
    this.adminDashboardService.getSummary().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.language.translate('admin.loadError'));
        this.loading.set(false);
      }
    });
  }

  metrics(summary: AdminDashboardSummary) {
    return [
      { label: 'admin.metric.products', value: summary.totalProducts, icon: Package },
      { label: 'admin.metric.activeProducts', value: summary.activeProducts, icon: Boxes },
      { label: 'admin.metric.users', value: summary.totalUsers, icon: Users },
      { label: 'admin.metric.orders', value: summary.totalOrders, icon: ReceiptText },
      { label: 'admin.metric.lowStock', value: summary.lowStockItems, icon: Boxes },
      { label: 'admin.metric.coupons', value: summary.totalCoupons, icon: BadgeDollarSign },
      { label: 'admin.metric.reviews', value: summary.totalReviews, icon: Star }
    ];
  }

  orderStatusRows(summary: AdminDashboardSummary) {
    return Object.entries(summary.ordersByStatus).map(([name, count]) => ({ name, count }));
  }
}
