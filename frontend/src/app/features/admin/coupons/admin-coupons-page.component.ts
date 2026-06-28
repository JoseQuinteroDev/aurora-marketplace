import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { LucideAngularModule, CircleOff, Pencil, Plus, Power, TicketPercent, X } from 'lucide-angular';
import { LanguageService } from '../../../core/i18n/language.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { AdminCoupon, AdminCouponRequest, CouponType } from '../../../core/models/admin-coupon.model';
import { AdminCouponService } from '../../../services/admin-coupon.service';
import { ToastService } from '../../../services/toast.service';
import { StatePanelComponent } from '../../../shared/state-panel/state-panel.component';
import { instantToLocalInput, localInputToInstant } from '../../../core/util/datetime';

// The app registers no custom LOCALE_ID, so Angular only ships en-US locale data.
// Format helper pipes are instantiated with it to mirror the template `| currency`/`| date`.
const DEFAULT_LOCALE = 'en-US';

@Component({
  selector: 'app-admin-coupons-page',
  imports: [NgClass, ReactiveFormsModule, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="px-4 py-8 sm:px-6 lg:px-8">
      <div class="max-w-7xl">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p class="text-xs font-bold uppercase tracking-[0.18em] text-aurora-pinebright">{{ 'admin.coupons.eyebrow' | t }}</p>
            <h1 class="mt-3 text-3xl font-extrabold tracking-normal text-white sm:text-4xl">{{ 'admin.coupons.title' | t }}</h1>
            <p class="mt-3 max-w-2xl text-sm leading-6 text-aurora-mist/70">{{ 'admin.coupons.subtitle' | t }}</p>
          </div>
          @if (!formOpen()) {
            <button class="ui-button ui-button-primary" type="button" (click)="openCreate()">
              <lucide-icon [img]="Plus" size="18" />
              {{ 'admin.coupons.new' | t }}
            </button>
          }
        </div>

        @if (formOpen()) {
          <div class="mt-8 rounded-ui border border-white/10 bg-white/[0.06] p-6">
            <div class="flex items-center justify-between gap-3">
              <h2 class="text-lg font-bold text-white">{{ (editingId() ? 'admin.coupons.editTitle' : 'admin.coupons.createTitle') | t }}</h2>
              <button class="cursor-pointer text-aurora-mist/70 hover:text-white" type="button" (click)="closeForm()" [attr.aria-label]="'common.close' | t">
                <lucide-icon [img]="X" size="20" />
              </button>
            </div>

            <form class="mt-6 grid gap-5 sm:grid-cols-2" [formGroup]="form" (ngSubmit)="save()">
              <label class="block sm:col-span-1">
                <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.coupons.field.code' | t }}</span>
                <input
                  class="ui-input mt-2 border-white/10 bg-white/[0.05] uppercase text-white"
                  formControlName="code"
                  type="text"
                  maxlength="80"
                  autocomplete="off"
                  [placeholder]="'admin.coupons.field.codePlaceholder' | t"
                  [attr.aria-invalid]="invalid('code')"
                  [attr.aria-describedby]="invalid('code') ? 'coupon-code-error' : null"
                />
                @if (invalid('code')) {
                  <span id="coupon-code-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ codeError() | t }}</span>
                }
              </label>

              <label class="block sm:col-span-1">
                <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.coupons.field.type' | t }}</span>
                <select class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white [&>option]:text-aurora-ink" formControlName="type">
                  <option value="PERCENTAGE">{{ 'admin.coupons.type.PERCENTAGE' | t }}</option>
                  <option value="FIXED_AMOUNT">{{ 'admin.coupons.type.FIXED_AMOUNT' | t }}</option>
                </select>
              </label>

              <label class="block sm:col-span-1">
                <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.coupons.field.value' | t }}</span>
                <input
                  class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                  formControlName="value"
                  type="number"
                  step="0.01"
                  min="0.01"
                  [attr.aria-invalid]="invalid('value')"
                  [attr.aria-describedby]="invalid('value') ? 'coupon-value-error' : null"
                />
                <span class="mt-1 block text-xs text-aurora-mist/60">{{ valueHint() | t }}</span>
                @if (invalid('value')) {
                  <span id="coupon-value-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.coupons.error.value' | t }}</span>
                }
              </label>

              <label class="flex items-center gap-3 sm:col-span-1 sm:self-end sm:pb-3">
                <input class="h-5 w-5 cursor-pointer rounded border-white/20 bg-white/[0.05] accent-aurora-pine" formControlName="active" type="checkbox" />
                <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.coupons.field.active' | t }}</span>
              </label>

              <label class="block sm:col-span-1">
                <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.coupons.field.startsAt' | t }}</span>
                <input class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white [color-scheme:dark]" formControlName="startsAt" type="datetime-local" />
              </label>

              <label class="block sm:col-span-1">
                <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.coupons.field.endsAt' | t }}</span>
                <input
                  class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white [color-scheme:dark]"
                  formControlName="endsAt"
                  type="datetime-local"
                  [attr.aria-invalid]="form.hasError('dateRange')"
                  [attr.aria-describedby]="form.hasError('dateRange') ? 'coupon-dates-error' : null"
                />
                @if (form.hasError('dateRange') && (form.controls.endsAt.dirty || form.controls.endsAt.touched)) {
                  <span id="coupon-dates-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.coupons.error.dateRange' | t }}</span>
                }
              </label>

              <label class="block sm:col-span-1">
                <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.coupons.field.maxUses' | t }}</span>
                <input
                  class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                  formControlName="maxUses"
                  type="number"
                  step="1"
                  min="1"
                  [placeholder]="'admin.coupons.field.unlimited' | t"
                  [attr.aria-invalid]="invalid('maxUses')"
                  [attr.aria-describedby]="invalid('maxUses') ? 'coupon-maxuses-error' : null"
                />
                @if (invalid('maxUses')) {
                  <span id="coupon-maxuses-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.coupons.error.minOne' | t }}</span>
                }
              </label>

              <label class="block sm:col-span-1">
                <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.coupons.field.maxUsesPerUser' | t }}</span>
                <input
                  class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                  formControlName="maxUsesPerUser"
                  type="number"
                  step="1"
                  min="1"
                  [placeholder]="'admin.coupons.field.unlimited' | t"
                  [attr.aria-invalid]="invalid('maxUsesPerUser')"
                  [attr.aria-describedby]="invalid('maxUsesPerUser') ? 'coupon-maxusesperuser-error' : null"
                />
                @if (invalid('maxUsesPerUser')) {
                  <span id="coupon-maxusesperuser-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.coupons.error.minOne' | t }}</span>
                }
              </label>

              <label class="block sm:col-span-1">
                <span class="text-sm font-bold text-aurora-mist/80">{{ 'admin.coupons.field.minimumOrderAmount' | t }}</span>
                <input
                  class="ui-input mt-2 border-white/10 bg-white/[0.05] text-white"
                  formControlName="minimumOrderAmount"
                  type="number"
                  step="0.01"
                  min="0"
                  [placeholder]="'admin.coupons.field.none' | t"
                  [attr.aria-invalid]="invalid('minimumOrderAmount')"
                  [attr.aria-describedby]="invalid('minimumOrderAmount') ? 'coupon-minorder-error' : null"
                />
                @if (invalid('minimumOrderAmount')) {
                  <span id="coupon-minorder-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'admin.coupons.error.minZero' | t }}</span>
                }
              </label>

              <div class="flex gap-3 sm:col-span-2">
                <button class="ui-button border border-white/10 bg-white/10 text-white hover:bg-white/15 flex-1" type="button" (click)="closeForm()">
                  {{ 'common.cancel' | t }}
                </button>
                <button class="ui-button ui-button-primary flex-1" type="submit" [disabled]="form.invalid || saving()">
                  {{ saving() ? ('admin.coupons.saving' | t) : ('admin.coupons.save' | t) }}
                </button>
              </div>
            </form>
          </div>
        }

        @if (loading()) {
          <div class="mt-8 grid gap-3">
            @for (item of [1, 2, 3, 4]; track item) {
              <div class="h-16 animate-pulse rounded-ui bg-white/10"></div>
            }
          </div>
        } @else if (error()) {
          <div class="mt-8">
            <app-state-panel mode="error" title="{{ 'admin.coupons.errorTitle' | t }}" [message]="error()!" />
            <button class="ui-button mt-6 border border-white/10 bg-white/10 text-white hover:bg-white/15" type="button" (click)="load()">
              {{ 'common.retry' | t }}
            </button>
          </div>
        } @else if (coupons().length === 0) {
          <div class="mt-8">
            <app-state-panel title="{{ 'admin.coupons.emptyTitle' | t }}" message="{{ 'admin.coupons.empty' | t }}" />
          </div>
        } @else {
          <div class="mt-8 hidden overflow-x-auto rounded-ui border border-white/10 lg:block">
            <table class="w-full text-left text-sm">
              <caption class="sr-only">{{ 'admin.coupons.title' | t }}</caption>
              <thead class="bg-white/[0.06] text-xs uppercase tracking-[0.12em] text-aurora-mist/70">
                <tr>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.coupons.col.code' | t }}</th>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.coupons.col.type' | t }}</th>
                  <th scope="col" class="px-4 py-3 text-right font-semibold">{{ 'admin.coupons.col.value' | t }}</th>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.coupons.col.limits' | t }}</th>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.coupons.col.validity' | t }}</th>
                  <th scope="col" class="px-4 py-3 font-semibold">{{ 'admin.coupons.col.state' | t }}</th>
                  <th scope="col" class="px-4 py-3 text-right font-semibold">{{ 'admin.coupons.col.actions' | t }}</th>
                </tr>
              </thead>
              <tbody>
                @for (coupon of coupons(); track coupon.id) {
                  <tr class="border-t border-white/10">
                    <td class="px-4 py-3 font-extrabold text-white">{{ coupon.code }}</td>
                    <td class="px-4 py-3 text-aurora-mist/80">{{ ('admin.coupons.type.' + coupon.type) | t }}</td>
                    <td class="px-4 py-3 text-right font-bold text-white">{{ formatValue(coupon) }}</td>
                    <td class="px-4 py-3 text-aurora-mist/70">{{ limitsLabel(coupon) }}</td>
                    <td class="px-4 py-3 text-aurora-mist/70">{{ validityLabel(coupon) }}</td>
                    <td class="px-4 py-3">
                      <span class="aurora-badge" [ngClass]="coupon.active ? 'bg-aurora-pine/20 text-aurora-pinebright' : 'bg-white/10 text-aurora-mist/70'">
                        {{ (coupon.active ? 'common.active' : 'common.inactive') | t }}
                      </span>
                    </td>
                    <td class="px-4 py-3">
                      <div class="flex items-center justify-end gap-2">
                        <button class="cursor-pointer rounded-ui p-2 text-aurora-mist/80 hover:bg-white/10 hover:text-white" type="button" (click)="openEdit(coupon)" [attr.aria-label]="('admin.coupons.editLabel' | t) + ' ' + coupon.code">
                          <lucide-icon [img]="Pencil" size="17" />
                        </button>
                        @if (coupon.active) {
                          <button class="cursor-pointer rounded-ui p-2 text-aurora-rose hover:bg-aurora-rose/15" type="button" (click)="deactivate(coupon)" [disabled]="rowBusy() === coupon.id" [attr.aria-label]="('admin.coupons.deactivateLabel' | t) + ' ' + coupon.code">
                            <lucide-icon [img]="CircleOff" size="17" />
                          </button>
                        } @else {
                          <button class="cursor-pointer rounded-ui p-2 text-aurora-pinebright hover:bg-aurora-pine/15" type="button" (click)="reactivate(coupon)" [disabled]="rowBusy() === coupon.id" [attr.aria-label]="('admin.coupons.reactivateLabel' | t) + ' ' + coupon.code">
                            <lucide-icon [img]="Power" size="17" />
                          </button>
                        }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="mt-8 grid gap-3 lg:hidden">
            @for (coupon of coupons(); track coupon.id) {
              <div class="rounded-ui border border-white/10 bg-white/[0.06] p-4">
                <div class="flex items-center justify-between gap-3">
                  <span class="flex items-center gap-2 font-extrabold text-white">
                    <lucide-icon class="text-aurora-pinebright" [img]="TicketPercent" size="18" />
                    {{ coupon.code }}
                  </span>
                  <span class="aurora-badge" [ngClass]="coupon.active ? 'bg-aurora-pine/20 text-aurora-pinebright' : 'bg-white/10 text-aurora-mist/70'">
                    {{ (coupon.active ? 'common.active' : 'common.inactive') | t }}
                  </span>
                </div>
                <dl class="mt-3 grid grid-cols-2 gap-2 text-sm">
                  <dt class="text-aurora-mist/60">{{ 'admin.coupons.col.type' | t }}</dt>
                  <dd class="text-right text-aurora-mist/80">{{ ('admin.coupons.type.' + coupon.type) | t }}</dd>
                  <dt class="text-aurora-mist/60">{{ 'admin.coupons.col.value' | t }}</dt>
                  <dd class="text-right font-bold text-white">{{ formatValue(coupon) }}</dd>
                  <dt class="text-aurora-mist/60">{{ 'admin.coupons.col.limits' | t }}</dt>
                  <dd class="text-right text-aurora-mist/80">{{ limitsLabel(coupon) }}</dd>
                  <dt class="text-aurora-mist/60">{{ 'admin.coupons.col.validity' | t }}</dt>
                  <dd class="text-right text-aurora-mist/80">{{ validityLabel(coupon) }}</dd>
                </dl>
                <div class="mt-4 flex gap-2">
                  <button class="ui-button border border-white/10 bg-white/10 text-white hover:bg-white/15 flex-1" type="button" (click)="openEdit(coupon)">
                    <lucide-icon [img]="Pencil" size="16" />
                    {{ 'admin.coupons.edit' | t }}
                  </button>
                  @if (coupon.active) {
                    <button class="ui-button border border-white/10 bg-white/10 text-aurora-rose hover:bg-white/15 flex-1" type="button" (click)="deactivate(coupon)" [disabled]="rowBusy() === coupon.id">
                      <lucide-icon [img]="CircleOff" size="16" />
                      {{ 'admin.coupons.deactivate' | t }}
                    </button>
                  } @else {
                    <button class="ui-button border border-white/10 bg-white/10 text-aurora-pinebright hover:bg-white/15 flex-1" type="button" (click)="reactivate(coupon)" [disabled]="rowBusy() === coupon.id">
                      <lucide-icon [img]="Power" size="16" />
                      {{ 'admin.coupons.reactivate' | t }}
                    </button>
                  }
                </div>
              </div>
            }
          </div>
        }
      </div>
    </section>
  `
})
export class AdminCouponsPageComponent {
  private readonly adminCouponService = inject(AdminCouponService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly language = inject(LanguageService);
  private readonly toast = inject(ToastService);

  readonly coupons = signal<AdminCoupon[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly formOpen = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly saving = signal(false);
  readonly rowBusy = signal<string | null>(null);
  readonly duplicateCode = signal(false);

  readonly CircleOff = CircleOff;
  readonly Pencil = Pencil;
  readonly Plus = Plus;
  readonly Power = Power;
  readonly TicketPercent = TicketPercent;
  readonly X = X;

  readonly form = this.formBuilder.nonNullable.group(
    {
      code: ['', [Validators.required, Validators.maxLength(80)]],
      type: ['PERCENTAGE' as CouponType, [Validators.required]],
      value: [null as number | null, [Validators.required, Validators.min(0.01)]],
      active: [true],
      startsAt: [''],
      endsAt: [''],
      maxUses: [null as number | null, [Validators.min(1)]],
      maxUsesPerUser: [null as number | null, [Validators.min(1)]],
      minimumOrderAmount: [null as number | null, [Validators.min(0)]]
    },
    { validators: [dateRangeValidator] }
  );

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.adminCouponService.listCoupons().subscribe({
      next: (coupons) => {
        this.coupons.set(coupons);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.language.translate('admin.coupons.error'));
        this.loading.set(false);
      }
    });
  }

  invalid(name: 'code' | 'value' | 'maxUses' | 'maxUsesPerUser' | 'minimumOrderAmount'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.dirty || control.touched);
  }

  codeError(): string {
    if (this.duplicateCode()) {
      return 'admin.coupons.error.duplicate';
    }
    return this.form.controls.code.hasError('maxlength') ? 'admin.coupons.error.codeLength' : 'admin.coupons.error.codeRequired';
  }

  valueHint(): string {
    return this.form.controls.type.value === 'PERCENTAGE' ? 'admin.coupons.hint.percentage' : 'admin.coupons.hint.fixed';
  }

  openCreate(): void {
    this.editingId.set(null);
    this.duplicateCode.set(false);
    this.form.reset({
      code: '',
      type: 'PERCENTAGE',
      value: null,
      active: true,
      startsAt: '',
      endsAt: '',
      maxUses: null,
      maxUsesPerUser: null,
      minimumOrderAmount: null
    });
    this.formOpen.set(true);
  }

  openEdit(coupon: AdminCoupon): void {
    this.editingId.set(coupon.id);
    this.duplicateCode.set(false);
    this.form.reset({
      code: coupon.code,
      type: coupon.type,
      value: coupon.value,
      active: coupon.active,
      startsAt: instantToLocalInput(coupon.startsAt),
      endsAt: instantToLocalInput(coupon.endsAt),
      maxUses: coupon.maxUses,
      maxUsesPerUser: coupon.maxUsesPerUser,
      minimumOrderAmount: coupon.minimumOrderAmount
    });
    this.formOpen.set(true);
  }

  closeForm(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
    this.duplicateCode.set(false);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const request: AdminCouponRequest = {
      code: raw.code.trim(),
      type: raw.type,
      value: raw.value ?? 0,
      active: raw.active,
      startsAt: localInputToInstant(raw.startsAt),
      endsAt: localInputToInstant(raw.endsAt),
      maxUses: raw.maxUses,
      maxUsesPerUser: raw.maxUsesPerUser,
      minimumOrderAmount: raw.minimumOrderAmount
    };

    this.saving.set(true);
    this.duplicateCode.set(false);
    const id = this.editingId();
    const call = id ? this.adminCouponService.updateCoupon(id, request) : this.adminCouponService.createCoupon(request);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(this.language.translate(id ? 'admin.coupons.updated' : 'admin.coupons.created'));
        this.closeForm();
        this.load();
      },
      error: (err: { status?: number; error?: { code?: string; validationErrors?: Record<string, string> } }) => {
        this.saving.set(false);
        if (err?.status === 409) {
          // Duplicate code — surface it on the code field.
          this.duplicateCode.set(true);
          this.form.controls.code.markAsTouched();
          this.form.controls.code.setErrors({ duplicate: true });
          this.toast.error(this.language.translate('admin.coupons.error.duplicate'));
        } else if (err?.status === 400 && err.error?.validationErrors) {
          this.applyValidationErrors(err.error.validationErrors);
          this.toast.error(this.language.translate('admin.coupons.error.validation'));
        } else {
          this.toast.error(this.language.translate('admin.coupons.saveError'));
        }
      }
    });
  }

  deactivate(coupon: AdminCoupon): void {
    this.rowBusy.set(coupon.id);
    this.adminCouponService.deleteCoupon(coupon.id).subscribe({
      next: () => {
        this.rowBusy.set(null);
        this.toast.success(this.language.translate('admin.coupons.deactivated'));
        this.load();
      },
      error: () => {
        this.rowBusy.set(null);
        this.toast.error(this.language.translate('admin.coupons.actionError'));
      }
    });
  }

  reactivate(coupon: AdminCoupon): void {
    this.rowBusy.set(coupon.id);
    const request: AdminCouponRequest = {
      code: coupon.code,
      type: coupon.type,
      value: coupon.value,
      active: true,
      startsAt: coupon.startsAt,
      endsAt: coupon.endsAt,
      maxUses: coupon.maxUses,
      maxUsesPerUser: coupon.maxUsesPerUser,
      minimumOrderAmount: coupon.minimumOrderAmount
    };
    this.adminCouponService.updateCoupon(coupon.id, request).subscribe({
      next: () => {
        this.rowBusy.set(null);
        this.toast.success(this.language.translate('admin.coupons.reactivated'));
        this.load();
      },
      error: () => {
        this.rowBusy.set(null);
        this.toast.error(this.language.translate('admin.coupons.actionError'));
      }
    });
  }

  formatValue(coupon: AdminCoupon): string {
    if (coupon.type === 'PERCENTAGE') {
      return `${coupon.value}%`;
    }
    return new CurrencyPipe(DEFAULT_LOCALE).transform(coupon.value) ?? String(coupon.value);
  }

  limitsLabel(coupon: AdminCoupon): string {
    const parts: string[] = [];
    if (coupon.maxUses != null) {
      parts.push(this.language.translate('admin.coupons.limit.total', { count: coupon.maxUses }));
    }
    if (coupon.maxUsesPerUser != null) {
      parts.push(this.language.translate('admin.coupons.limit.perUser', { count: coupon.maxUsesPerUser }));
    }
    if (coupon.minimumOrderAmount != null) {
      const amount = new CurrencyPipe(DEFAULT_LOCALE).transform(coupon.minimumOrderAmount) ?? String(coupon.minimumOrderAmount);
      parts.push(this.language.translate('admin.coupons.limit.minOrder', { amount }));
    }
    return parts.length ? parts.join(' · ') : this.language.translate('admin.coupons.limit.none');
  }

  validityLabel(coupon: AdminCoupon): string {
    const datePipe = new DatePipe(DEFAULT_LOCALE);
    const from = coupon.startsAt ? datePipe.transform(coupon.startsAt, 'shortDate') : null;
    const to = coupon.endsAt ? datePipe.transform(coupon.endsAt, 'shortDate') : null;
    if (!from && !to) {
      return this.language.translate('admin.coupons.validity.always');
    }
    if (from && to) {
      return `${from} – ${to}`;
    }
    if (from) {
      return this.language.translate('admin.coupons.validity.from', { date: from });
    }
    return this.language.translate('admin.coupons.validity.until', { date: to as string });
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

/** Group validator: endsAt must not be before startsAt when both are set. */
function dateRangeValidator(group: AbstractControl): ValidationErrors | null {
  const starts = group.get('startsAt')?.value as string;
  const ends = group.get('endsAt')?.value as string;
  if (!starts || !ends) {
    return null;
  }
  return new Date(ends).getTime() >= new Date(starts).getTime() ? null : { dateRange: true };
}
