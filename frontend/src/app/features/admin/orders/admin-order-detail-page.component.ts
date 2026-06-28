import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowLeft, CreditCard, PackageCheck, ReceiptText, RefreshCw } from 'lucide-angular';
import { LanguageService } from '../../../core/i18n/language.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { AdminOrder, OrderStatus, allowedTransitions } from '../../../core/models/admin-order.model';
import { AdminOrderService } from '../../../services/admin-order.service';
import { ToastService } from '../../../services/toast.service';
import { StatePanelComponent } from '../../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-admin-order-detail-page',
  imports: [CurrencyPipe, DatePipe, NgClass, ReactiveFormsModule, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="px-4 py-8 sm:px-6 lg:px-8">
      <div class="max-w-7xl">
        <a routerLink="/admin/orders" class="inline-flex cursor-pointer items-center gap-2 text-sm font-semibold text-aurora-pinebright hover:underline">
          <lucide-icon [img]="ArrowLeft" size="17" />
          {{ 'admin.orders.title' | t }}
        </a>

        @if (loading()) {
          <div class="mt-6 grid gap-6 lg:grid-cols-[1fr_380px]">
            <div class="h-96 animate-pulse rounded-ui bg-white/10"></div>
            <div class="h-80 animate-pulse rounded-ui bg-white/10"></div>
          </div>
        } @else if (error()) {
          <div class="mt-6">
            <app-state-panel mode="error" title="{{ 'admin.orders.errorTitle' | t }}" [message]="error()!" />
            <button class="ui-button mt-6 border border-white/10 bg-white/10 text-white hover:bg-white/15" type="button" (click)="load()">
              {{ 'common.retry' | t }}
            </button>
          </div>
        } @else if (order(); as data) {
          <div class="mt-6 grid gap-6 lg:grid-cols-[1fr_380px]">
            <div class="space-y-6">
              <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6">
                <p class="text-xs font-bold uppercase tracking-[0.18em] text-aurora-pinebright">{{ 'admin.orders.detailEyebrow' | t }}</p>
                <div class="mt-3 flex flex-wrap items-center gap-3">
                  <h1 class="text-3xl font-extrabold text-white sm:text-4xl">{{ data.orderNumber }}</h1>
                  <span class="aurora-badge" [ngClass]="statusClass(data.status)">{{ ('order.status.' + data.status) | t }}</span>
                </div>
                <p class="mt-3 text-sm text-aurora-mist/70">{{ data.createdAt | date: 'medium' }}</p>
              </div>

              <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6">
                <div class="flex items-center gap-2">
                  <lucide-icon class="text-aurora-pinebright" [img]="PackageCheck" size="20" />
                  <h2 class="text-lg font-bold text-white">{{ 'admin.orders.items' | t }}</h2>
                </div>
                <div class="mt-5 grid gap-3">
                  @for (item of data.items; track item.id) {
                    <div class="grid gap-2 rounded-ui bg-white/[0.05] p-4 sm:grid-cols-[1fr_auto] sm:items-center">
                      <div>
                        <p class="font-extrabold text-white">{{ item.productName }}</p>
                        <p class="mt-1 text-sm text-aurora-mist/70">{{ item.variantName }} / {{ item.variantSku }}</p>
                        <p class="mt-2 text-sm font-semibold text-aurora-mist/70">{{ item.quantity }} x {{ item.unitPrice | currency }}</p>
                      </div>
                      <p class="text-lg font-extrabold text-white sm:text-right">{{ item.lineTotal | currency }}</p>
                    </div>
                  }
                </div>
              </div>

              <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6">
                <div class="flex items-center gap-2">
                  <lucide-icon class="text-aurora-pinebright" [img]="ReceiptText" size="20" />
                  <h2 class="text-lg font-bold text-white">{{ 'admin.orders.timeline' | t }}</h2>
                </div>
                <ol class="mt-5 grid gap-4">
                  @for (history of data.statusHistory; track history.id) {
                    <li class="flex gap-3">
                      <span class="mt-1 h-3 w-3 shrink-0 rounded-full bg-aurora-pinebright"></span>
                      <div>
                        <p class="font-extrabold text-white">{{ ('order.status.' + history.status) | t }}</p>
                        <p class="text-sm text-aurora-mist/70">{{ history.createdAt | date: 'medium' }}</p>
                        @if (history.note) {
                          <p class="mt-1 text-sm text-aurora-mist/80">{{ history.note }}</p>
                        }
                        <p class="mt-1 text-xs text-aurora-mist/50">
                          @if (history.changedByUserId) {
                            {{ 'admin.orders.changedBy' | t }} {{ history.changedByUserId }}
                          } @else {
                            {{ 'admin.orders.changedBySystem' | t }}
                          }
                        </p>
                      </div>
                    </li>
                  } @empty {
                    <li class="text-sm text-aurora-mist/70">{{ ('order.status.' + data.status) | t }}</li>
                  }
                </ol>
              </div>
            </div>

            <aside class="space-y-6 lg:sticky lg:top-24 lg:h-fit">
              <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6">
                <div class="flex items-center gap-2">
                  <lucide-icon class="text-aurora-pinebright" [img]="CreditCard" size="20" />
                  <h2 class="text-lg font-bold text-white">{{ 'cart.summary' | t }}</h2>
                </div>
                <div class="mt-5 space-y-3 text-sm">
                  <div class="flex justify-between text-aurora-mist/70">
                    <span>{{ 'cart.subtotal' | t }}</span>
                    <span class="font-bold text-white">{{ data.subtotal | currency }}</span>
                  </div>
                  <div class="flex justify-between text-aurora-mist/70">
                    <span>{{ 'cart.discount' | t }}</span>
                    <span class="font-bold text-aurora-pinebright">-{{ data.discountTotal | currency }}</span>
                  </div>
                  @if (data.couponCode) {
                    <div class="flex justify-between text-aurora-mist/70">
                      <span>{{ 'cart.coupon' | t }}</span>
                      <span class="font-bold text-white">{{ data.couponCode }}</span>
                    </div>
                  }
                  <div class="border-t border-white/10 pt-3 text-lg font-extrabold text-white">
                    <div class="flex justify-between">
                      <span>{{ 'cart.total' | t }}</span>
                      <span>{{ data.total | currency }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <div class="rounded-ui border border-white/10 bg-white/[0.06] p-6">
                <div class="flex items-center gap-2">
                  <lucide-icon class="text-aurora-pinebright" [img]="RefreshCw" size="20" />
                  <h2 class="text-lg font-bold text-white">{{ 'admin.orders.changeStatus' | t }}</h2>
                </div>

                @if (targets().length === 0) {
                  <p class="mt-5 rounded-ui bg-white/[0.05] px-4 py-3 text-sm text-aurora-mist/70">{{ 'admin.orders.terminal' | t }}</p>
                } @else {
                  <form class="mt-5 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
                    <label class="block">
                      <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.orders.newStatus' | t }}</span>
                      <select
                        class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white [&>option]:text-aurora-ink"
                        formControlName="status"
                        [attr.aria-invalid]="statusInvalid()"
                        [attr.aria-describedby]="statusInvalid() ? 'admin-status-error' : null"
                      >
                        <option value="" disabled>{{ 'admin.orders.selectStatus' | t }}</option>
                        @for (target of targets(); track target) {
                          <option [value]="target">{{ ('order.status.' + target) | t }}</option>
                        }
                      </select>
                      @if (statusInvalid()) {
                        <span id="admin-status-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.orders.statusRequired' | t }}</span>
                      }
                    </label>

                    <label class="block">
                      <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.orders.noteLabel' | t }}</span>
                      <textarea
                        class="ui-input mt-2 h-auto min-h-20 resize-y border-white/10 bg-white/[0.05] py-2 text-white"
                        formControlName="note"
                        rows="3"
                        maxlength="255"
                        [placeholder]="'admin.orders.notePlaceholder' | t"
                        [attr.aria-invalid]="noteInvalid()"
                        [attr.aria-describedby]="noteInvalid() ? 'admin-note-error' : null"
                      ></textarea>
                      @if (noteInvalid()) {
                        <span id="admin-note-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.orders.noteTooLong' | t }}</span>
                      }
                    </label>

                    @if (conflict()) {
                      <app-state-panel mode="error" title="{{ 'admin.orders.conflictTitle' | t }}" [message]="'admin.orders.conflict' | t" />
                    }

                    <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="form.invalid || submitting()">
                      {{ submitting() ? ('admin.orders.saving' | t) : ('admin.orders.saveStatus' | t) }}
                    </button>
                  </form>
                }
              </div>
            </aside>
          </div>
        }
      </div>
    </section>
  `
})
export class AdminOrderDetailPageComponent {
  private readonly adminOrderService = inject(AdminOrderService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly language = inject(LanguageService);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  readonly order = signal<AdminOrder | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly conflict = signal(false);

  /** Legal next statuses for the loaded order's current status. */
  readonly targets = computed<readonly OrderStatus[]>(() => {
    const current = this.order();
    return current ? allowedTransitions(current.status) : [];
  });

  readonly ArrowLeft = ArrowLeft;
  readonly CreditCard = CreditCard;
  readonly PackageCheck = PackageCheck;
  readonly ReceiptText = ReceiptText;
  readonly RefreshCw = RefreshCw;

  readonly form = this.formBuilder.nonNullable.group({
    status: ['' as OrderStatus | '', [Validators.required]],
    note: ['', [Validators.maxLength(255)]]
  });

  private id: string | null = null;

  constructor() {
    this.id = this.route.snapshot.paramMap.get('id');
    this.load();
  }

  load(): void {
    if (!this.id) {
      this.error.set(this.language.translate('admin.orders.error'));
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.adminOrderService.getOrder(this.id).subscribe({
      next: (order) => {
        this.order.set(order);
        this.resetForm();
        this.loading.set(false);
      },
      error: (err: { status?: number }) => {
        this.error.set(this.language.translate(err?.status === 404 ? 'admin.orders.notFound' : 'admin.orders.error'));
        this.loading.set(false);
      }
    });
  }

  statusInvalid(): boolean {
    const control = this.form.controls.status;
    return control.invalid && (control.dirty || control.touched);
  }

  noteInvalid(): boolean {
    const control = this.form.controls.note;
    return control.invalid && (control.dirty || control.touched);
  }

  submit(): void {
    if (!this.id || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const status = raw.status;
    if (!status) {
      return;
    }

    const note = raw.note.trim();
    this.submitting.set(true);
    this.conflict.set(false);
    this.adminOrderService.updateStatus(this.id, note ? { status, note } : { status }).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.resetForm();
        this.submitting.set(false);
        this.toast.success(this.language.translate('admin.orders.statusUpdated'));
      },
      error: (err: { status?: number }) => {
        this.submitting.set(false);
        // 409 — illegal transition or a concurrent edit. Refetch so the form reflects
        // the true current status, and surface a clear "please retry" message.
        if (err?.status === 409) {
          this.conflict.set(true);
          this.refetchAfterConflict();
        } else {
          this.toast.error(this.language.translate('admin.orders.updateError'));
        }
      }
    });
  }

  private refetchAfterConflict(): void {
    if (!this.id) {
      return;
    }
    this.adminOrderService.getOrder(this.id).subscribe({
      next: (order) => {
        this.order.set(order);
        this.resetForm();
      },
      error: () => {}
    });
  }

  private resetForm(): void {
    this.form.reset({ status: '', note: '' });
  }

  statusClass(status: OrderStatus): string {
    if (status === 'PAID' || status === 'DELIVERED') {
      return 'bg-aurora-pine/20 text-aurora-pinebright';
    }
    if (status === 'CANCELLED' || status === 'REFUNDED') {
      return 'bg-aurora-rose/20 text-aurora-rose';
    }
    return 'bg-white/10 text-aurora-mist';
  }
}
