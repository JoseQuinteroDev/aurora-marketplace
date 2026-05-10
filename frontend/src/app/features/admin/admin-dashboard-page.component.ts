import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { LucideAngularModule, BadgeDollarSign, Boxes, Package, ReceiptText, Star, Users } from 'lucide-angular';
import { AdminDashboardSummary } from '../../core/models/admin.model';
import { AdminDashboardService } from '../../services/admin-dashboard.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-admin-dashboard-page',
  imports: [CurrencyPipe, LucideAngularModule, StatePanelComponent],
  template: `
    <section class="px-4 py-8 sm:px-6 lg:px-8">
      <div class="max-w-7xl">
        <p class="text-xs font-bold uppercase tracking-[0.18em] text-emerald-300">Aurora admin</p>
        <h1 class="mt-3 text-3xl font-black tracking-normal text-white sm:text-4xl">Dashboard summary</h1>
        <p class="mt-3 max-w-2xl text-sm leading-6 text-slate-300">Connected to the admin dashboard summary API and ready for richer operational screens.</p>

        @if (loading()) {
          <div class="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            @for (item of [1, 2, 3, 4, 5, 6, 7, 8]; track item) {
              <div class="h-32 animate-pulse rounded-ui bg-white/10"></div>
            }
          </div>
        } @else if (error()) {
          <div class="mt-8">
            <app-state-panel mode="error" title="Dashboard unavailable" [message]="error()!" />
          </div>
        } @else if (summary(); as data) {
          <div class="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            @for (metric of metrics(data); track metric.label) {
              <div class="rounded-ui border border-white/10 bg-white/[0.06] p-5">
                <div class="flex items-center justify-between gap-4">
                  <p class="text-sm font-semibold text-slate-300">{{ metric.label }}</p>
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
              <h2 class="text-lg font-bold text-white">Orders by status</h2>
              <div class="mt-5 grid gap-3">
                @for (status of orderStatusRows(data); track status.name) {
                  <div class="flex items-center justify-between rounded-ui bg-white/[0.08] px-4 py-3 text-sm">
                    <span class="font-semibold text-slate-300">{{ status.name }}</span>
                    <span class="font-black text-white">{{ status.count }}</span>
                  </div>
                } @empty {
                  <p class="text-sm text-slate-400">No order data yet.</p>
                }
              </div>
            </div>

            <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6">
              <h2 class="text-lg font-bold text-white">Paid revenue</h2>
              <p class="mt-5 text-4xl font-black text-emerald-300">{{ data.totalRevenuePaid | currency }}</p>
              <p class="mt-3 text-sm leading-6 text-slate-300">Revenue is based on orders with PAID status from the backend MVP.</p>
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

  readonly Package = Package;
  readonly Boxes = Boxes;
  readonly Users = Users;
  readonly ReceiptText = ReceiptText;
  readonly BadgeDollarSign = BadgeDollarSign;
  readonly Star = Star;

  constructor(private readonly adminDashboardService: AdminDashboardService) {}

  ngOnInit(): void {
    this.adminDashboardService.getSummary().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Sign in as ADMIN and ensure the backend is running on port 8080.');
        this.loading.set(false);
      }
    });
  }

  metrics(summary: AdminDashboardSummary) {
    return [
      { label: 'Products', value: summary.totalProducts, icon: Package },
      { label: 'Active products', value: summary.activeProducts, icon: Boxes },
      { label: 'Users', value: summary.totalUsers, icon: Users },
      { label: 'Orders', value: summary.totalOrders, icon: ReceiptText },
      { label: 'Low stock', value: summary.lowStockItems, icon: Boxes },
      { label: 'Coupons', value: summary.totalCoupons, icon: BadgeDollarSign },
      { label: 'Reviews', value: summary.totalReviews, icon: Star }
    ];
  }

  orderStatusRows(summary: AdminDashboardSummary) {
    return Object.entries(summary.ordersByStatus).map(([name, count]) => ({ name, count }));
  }
}
