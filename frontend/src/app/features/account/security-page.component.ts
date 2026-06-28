import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, Check, Copy, KeyRound, ShieldCheck, Smartphone } from 'lucide-angular';
import { LanguageService } from '../../core/i18n/language.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { MfaEnrollResponse } from '../../core/models/auth.model';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

/**
 * Account two-factor-authentication (TOTP) management.
 *
 * Loads the current MFA status, then drives one of two flows:
 *  - DISABLED → enroll (shows the Base32 secret + otpauth URI for MANUAL entry into an
 *    authenticator app), then confirm with a 6-digit code to enable.
 *  - ENABLED  → disable, gated on a current 6-digit code.
 *
 * NOTE: a scannable QR code is a deliberate fast-follow — rendering one would require a new
 * QR dependency, which this change intentionally avoids. The secret + otpauth URI cover
 * manual key entry, which every authenticator app supports.
 */
@Component({
  selector: 'app-security-page',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell flex min-h-[600px] items-start justify-center py-10">
      <div class="mx-auto w-full max-w-xl">
        <div class="surface-panel p-6 sm:p-8">
          <p class="section-kicker">{{ 'mfa.page.kicker' | t }}</p>
          <h1 class="mt-3 text-4xl font-semibold text-aurora-ink dark:text-white">{{ 'mfa.page.title' | t }}</h1>
          <p class="mt-3 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'mfa.page.subtitle' | t }}</p>

          @if (statusLoading()) {
            <div class="mt-8">
              <app-state-panel mode="empty" title="{{ 'mfa.page.loadingTitle' | t }}" [message]="'mfa.page.loading' | t" />
            </div>
          } @else if (statusError()) {
            <div class="mt-8">
              <app-state-panel mode="error" title="{{ 'mfa.page.title' | t }}" [message]="'mfa.page.statusError' | t" />
            </div>
            <button class="ui-button ui-button-secondary mt-6 w-full" type="button" (click)="loadStatus()">{{ 'mfa.page.retry' | t }}</button>
          } @else {
            <!-- ENABLED: show status + a code-gated disable flow. -->
            @if (enabled()) {
              <div class="mt-8 flex items-center gap-3 rounded-ui border border-aurora-line bg-white p-4 dark:border-white/10 dark:bg-white/5">
                <lucide-icon class="text-aurora-emerald" [img]="ShieldCheck" size="20" />
                <div>
                  <p class="font-extrabold text-aurora-ink dark:text-white">{{ 'mfa.status.enabledTitle' | t }}</p>
                  <p class="mt-1 text-sm text-aurora-muted dark:text-stone-300">{{ 'mfa.status.enabled' | t }}</p>
                </div>
              </div>

              <form class="mt-6 space-y-4" [formGroup]="disableForm" (ngSubmit)="disable()">
                <label class="block">
                  <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'mfa.disable.codeLabel' | t }}</span>
                  <span class="field-shell" [class.field-shell-invalid]="codeInvalid(disableForm)">
                    <lucide-icon class="text-stone-400" [img]="KeyRound" size="17" />
                    <input class="h-11 min-w-0 flex-1 bg-transparent text-sm tracking-[0.4em] outline-none dark:text-white" formControlName="code" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" [placeholder]="'mfa.codePlaceholder' | t" [attr.aria-invalid]="codeInvalid(disableForm)" [attr.aria-describedby]="codeInvalid(disableForm) ? 'mfa-disable-error' : null" />
                  </span>
                  @if (codeInvalid(disableForm)) {
                    <span id="mfa-disable-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'mfa.codeInvalid' | t }}</span>
                  }
                </label>

                @if (error()) {
                  <app-state-panel mode="error" title="{{ 'mfa.disable.failed' | t }}" [message]="error()!" />
                }

                <button class="ui-button ui-button-secondary w-full text-aurora-rose" type="submit" [disabled]="disableForm.invalid || busy()">
                  {{ busy() ? ('mfa.disable.loading' | t) : ('mfa.disable.submit' | t) }}
                </button>
              </form>
            } @else if (!enrollment()) {
              <!-- DISABLED, not yet enrolling: the entry point. -->
              <div class="mt-8 flex items-center gap-3 rounded-ui border border-aurora-line bg-white p-4 dark:border-white/10 dark:bg-white/5">
                <lucide-icon class="text-aurora-muted" [img]="Smartphone" size="20" />
                <div>
                  <p class="font-extrabold text-aurora-ink dark:text-white">{{ 'mfa.status.disabledTitle' | t }}</p>
                  <p class="mt-1 text-sm text-aurora-muted dark:text-stone-300">{{ 'mfa.status.disabled' | t }}</p>
                </div>
              </div>

              @if (error()) {
                <div class="mt-6"><app-state-panel mode="error" title="{{ 'mfa.enroll.failed' | t }}" [message]="error()!" /></div>
              }

              <button class="ui-button ui-button-primary mt-6 w-full" type="button" (click)="enroll()" [disabled]="busy()">
                <lucide-icon [img]="ShieldCheck" size="18" />
                {{ busy() ? ('mfa.enroll.loading' | t) : ('mfa.enroll.cta' | t) }}
              </button>
            } @else {
              <!-- DISABLED, enrolling: show secret + otpauth URI, then confirm with a code. -->
              <ol class="mt-8 space-y-5">
                <li>
                  <p class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'mfa.enroll.step1' | t }}</p>
                  <div class="mt-2 flex items-stretch gap-2">
                    <code class="block min-w-0 flex-1 overflow-x-auto rounded-ui border border-aurora-line bg-stone-50 px-3 py-3 font-mono text-sm tracking-wider text-aurora-ink dark:border-white/10 dark:bg-white/5 dark:text-white">{{ enrollment()!.secret }}</code>
                    <button class="ui-button ui-button-secondary h-auto px-3" type="button" (click)="copy(enrollment()!.secret, 'secret')" [attr.aria-label]="'mfa.enroll.copySecret' | t">
                      <lucide-icon [img]="copied() === 'secret' ? Check : Copy" size="17" />
                    </button>
                  </div>
                </li>
                <li>
                  <p class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'mfa.enroll.step2' | t }}</p>
                  <p class="mt-1 text-xs leading-5 text-aurora-muted dark:text-stone-300">{{ 'mfa.enroll.step2Hint' | t }}</p>
                  <div class="mt-2 flex items-stretch gap-2">
                    <code class="block min-w-0 flex-1 overflow-x-auto rounded-ui border border-aurora-line bg-stone-50 px-3 py-3 font-mono text-xs text-aurora-ink dark:border-white/10 dark:bg-white/5 dark:text-white">{{ enrollment()!.otpauthUri }}</code>
                    <button class="ui-button ui-button-secondary h-auto px-3" type="button" (click)="copy(enrollment()!.otpauthUri, 'uri')" [attr.aria-label]="'mfa.enroll.copyUri' | t">
                      <lucide-icon [img]="copied() === 'uri' ? Check : Copy" size="17" />
                    </button>
                  </div>
                </li>
              </ol>

              <form class="mt-6 space-y-4" [formGroup]="confirmForm" (ngSubmit)="confirm()">
                <label class="block">
                  <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'mfa.enroll.step3' | t }}</span>
                  <span class="field-shell" [class.field-shell-invalid]="codeInvalid(confirmForm)">
                    <lucide-icon class="text-stone-400" [img]="KeyRound" size="17" />
                    <input class="h-11 min-w-0 flex-1 bg-transparent text-sm tracking-[0.4em] outline-none dark:text-white" formControlName="code" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" [placeholder]="'mfa.codePlaceholder' | t" [attr.aria-invalid]="codeInvalid(confirmForm)" [attr.aria-describedby]="codeInvalid(confirmForm) ? 'mfa-confirm-error' : null" />
                  </span>
                  @if (codeInvalid(confirmForm)) {
                    <span id="mfa-confirm-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'mfa.codeInvalid' | t }}</span>
                  }
                </label>

                @if (error()) {
                  <app-state-panel mode="error" title="{{ 'mfa.confirm.failed' | t }}" [message]="error()!" />
                }

                <div class="flex gap-2">
                  <button class="ui-button ui-button-secondary flex-1" type="button" (click)="cancelEnroll()" [disabled]="busy()">{{ 'mfa.confirm.cancel' | t }}</button>
                  <button class="ui-button ui-button-primary flex-1" type="submit" [disabled]="confirmForm.invalid || busy()">
                    {{ busy() ? ('mfa.confirm.loading' | t) : ('mfa.confirm.submit' | t) }}
                  </button>
                </div>
              </form>
            }
          }

          <p class="mt-6 text-center text-sm text-aurora-muted dark:text-stone-300">
            <a routerLink="/account/orders" class="premium-link">{{ 'mfa.page.backToAccount' | t }}</a>
          </p>
        </div>
      </div>
    </section>
  `
})
export class SecurityPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly language = inject(LanguageService);
  private readonly toast = inject(ToastService);

  readonly statusLoading = signal(true);
  readonly statusError = signal(false);
  readonly enabled = signal(false);
  readonly enrollment = signal<MfaEnrollResponse | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly copied = signal<'secret' | 'uri' | null>(null);

  readonly Check = Check;
  readonly Copy = Copy;
  readonly KeyRound = KeyRound;
  readonly ShieldCheck = ShieldCheck;
  readonly Smartphone = Smartphone;

  readonly confirmForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
  });

  readonly disableForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
  });

  constructor() {
    this.loadStatus();
  }

  loadStatus(): void {
    this.statusLoading.set(true);
    this.statusError.set(false);
    this.authService.mfaStatus().subscribe({
      next: (response) => {
        this.enabled.set(response.data.enabled);
        this.statusLoading.set(false);
      },
      error: () => {
        this.statusError.set(true);
        this.statusLoading.set(false);
      }
    });
  }

  codeInvalid(form: typeof this.confirmForm): boolean {
    const control = form.controls.code;
    return control.invalid && (control.dirty || control.touched);
  }

  enroll(): void {
    this.busy.set(true);
    this.error.set(null);
    this.authService.enrollMfa().subscribe({
      next: (response) => {
        this.enrollment.set(response.data);
        this.busy.set(false);
      },
      error: () => {
        this.error.set(this.language.translate('mfa.enroll.error'));
        this.busy.set(false);
      }
    });
  }

  confirm(): void {
    if (this.confirmForm.invalid) {
      this.confirmForm.markAllAsTouched();
      return;
    }

    this.busy.set(true);
    this.error.set(null);
    this.authService.confirmMfa(this.confirmForm.getRawValue().code).subscribe({
      next: () => {
        this.enabled.set(true);
        this.enrollment.set(null);
        this.confirmForm.reset();
        this.busy.set(false);
        this.toast.success(this.language.translate('mfa.confirm.success'));
      },
      error: (err: { status?: number; error?: { code?: string } }) => {
        const key = err?.error?.code === 'MFA_INVALID_CODE' || err?.status === 400
          ? 'mfa.codeWrong'
          : 'mfa.confirm.error';
        this.error.set(this.language.translate(key));
        this.busy.set(false);
      }
    });
  }

  cancelEnroll(): void {
    this.enrollment.set(null);
    this.confirmForm.reset();
    this.error.set(null);
  }

  disable(): void {
    if (this.disableForm.invalid) {
      this.disableForm.markAllAsTouched();
      return;
    }

    this.busy.set(true);
    this.error.set(null);
    this.authService.disableMfa(this.disableForm.getRawValue().code).subscribe({
      next: () => {
        this.enabled.set(false);
        this.disableForm.reset();
        this.busy.set(false);
        this.toast.success(this.language.translate('mfa.disable.success'));
      },
      error: (err: { status?: number; error?: { code?: string } }) => {
        const key = err?.error?.code === 'MFA_INVALID_CODE' || err?.status === 400
          ? 'mfa.codeWrong'
          : 'mfa.disable.error';
        this.error.set(this.language.translate(key));
        this.busy.set(false);
      }
    });
  }

  copy(value: string, which: 'secret' | 'uri'): void {
    if (typeof navigator === 'undefined' || !navigator.clipboard) {
      return;
    }
    navigator.clipboard.writeText(value).then(
      () => {
        this.copied.set(which);
        this.toast.success(this.language.translate('mfa.enroll.copied'));
        setTimeout(() => this.copied.set(null), 2000);
      },
      () => {}
    );
  }
}
