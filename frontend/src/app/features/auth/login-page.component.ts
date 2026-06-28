import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowRight, KeyRound, LockKeyhole, Mail, ShieldCheck, Sparkles } from 'lucide-angular';
import { LanguageService } from '../../core/i18n/language.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { AuthPayload } from '../../core/models/auth.model';
import { ApiResponse } from '../../core/models/api-response.model';
import { safeInternalUrl } from '../../core/util/safe-internal-url';
import { AuthService } from '../../services/auth.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell grid min-h-[760px] items-center gap-10 py-10 lg:grid-cols-[0.96fr_1.04fr]">
      <div class="relative hidden min-h-[660px] overflow-hidden rounded-ui bg-aurora-night shadow-premium lg:block">
        <img class="absolute inset-0 h-full w-full object-cover opacity-75" src="https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?auto=format&fit=crop&w=1400&q=85" [attr.alt]="'a11y.loginAlt' | t" />
        <div class="absolute inset-0 bg-gradient-to-t from-aurora-night via-aurora-night/40 to-transparent"></div>
        <div class="absolute bottom-0 left-0 right-0 p-8 text-white">
          <div class="inline-flex items-center gap-2 rounded-ui border border-white/15 bg-white/10 px-3 py-2 text-xs font-extrabold uppercase tracking-[0.16em] text-aurora-pinebright">
            <lucide-icon [img]="Sparkles" size="14" />
            {{ 'auth.memberAccess' | t }}
          </div>
          <h1 class="mt-5 max-w-md text-4xl font-extrabold leading-tight">{{ 'auth.login.title' | t }}</h1>
          <div class="mt-6 grid gap-3">
            @for (item of panelItems; track item) {
              <div class="flex items-center gap-3 rounded-ui border border-white/10 bg-white/10 p-3">
                <lucide-icon class="text-aurora-pinebright" [img]="ShieldCheck" size="18" />
                <span class="text-sm font-semibold text-stone-100">{{ item | t }}</span>
              </div>
            }
          </div>
        </div>
      </div>

      <div class="mx-auto w-full max-w-md">
        @if (mfaRequired()) {
        <div class="surface-panel p-6 sm:p-8">
          <p class="section-kicker">{{ 'mfa.challenge.kicker' | t }}</p>
          <h1 class="mt-3 text-4xl font-semibold text-aurora-ink dark:text-white">{{ 'mfa.challenge.title' | t }}</h1>
          <p class="mt-3 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'mfa.challenge.subtitle' | t }}</p>

          <form class="mt-8 space-y-4" [formGroup]="mfaForm" (ngSubmit)="submitMfa()">
            <label class="block">
              <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'mfa.codeLabel' | t }}</span>
              <span class="field-shell" [class.field-shell-invalid]="mfaCodeInvalid()">
                <lucide-icon class="text-stone-400" [img]="KeyRound" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm tracking-[0.4em] outline-none dark:text-white" formControlName="code" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" [placeholder]="'mfa.codePlaceholder' | t" [attr.aria-invalid]="mfaCodeInvalid()" [attr.aria-describedby]="mfaCodeInvalid() ? 'mfa-code-error' : null" />
              </span>
              @if (mfaCodeInvalid()) {
                <span id="mfa-code-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'mfa.codeInvalid' | t }}</span>
              }
            </label>

            @if (error()) {
              <app-state-panel mode="error" title="{{ 'mfa.challenge.failed' | t }}" [message]="error()!" />
            }

            <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="mfaForm.invalid || loading()">
              <lucide-icon [img]="ShieldCheck" size="18" />
              {{ loading() ? ('mfa.challenge.loading' | t) : ('mfa.challenge.submit' | t) }}
            </button>
          </form>

          <p class="mt-6 text-center text-sm text-aurora-muted dark:text-stone-300">
            <button type="button" class="premium-link" (click)="backToLogin()">{{ 'mfa.challenge.back' | t }}</button>
          </p>
        </div>
        } @else {
        <div class="surface-panel p-6 sm:p-8">
          <p class="section-kicker">{{ 'nav.signIn' | t }}</p>
          <h1 class="mt-3 text-4xl font-semibold text-aurora-ink dark:text-white">{{ 'auth.login.title' | t }}</h1>
          <p class="mt-3 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'auth.login.subtitle' | t }}</p>

          <form class="mt-8 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
            <label class="block">
              <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'auth.email' | t }}</span>
              <span class="field-shell" [class.field-shell-invalid]="emailInvalid()">
                <lucide-icon class="text-stone-400" [img]="Mail" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="email" type="email" autocomplete="email" [placeholder]="'auth.placeholder.email' | t" [attr.aria-invalid]="emailInvalid()" [attr.aria-describedby]="emailInvalid() ? 'login-email-error' : null" />
              </span>
              @if (emailInvalid()) {
                <span id="login-email-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'auth.emailInvalid' | t }}</span>
              }
            </label>

            <label class="block">
              <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'auth.password' | t }}</span>
              <span class="field-shell" [class.field-shell-invalid]="passwordInvalid()">
                <lucide-icon class="text-stone-400" [img]="LockKeyhole" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="password" type="password" autocomplete="current-password" [placeholder]="'auth.placeholder.password' | t" [attr.aria-invalid]="passwordInvalid()" [attr.aria-describedby]="passwordInvalid() ? 'login-password-error' : null" />
              </span>
              @if (passwordInvalid()) {
                <span id="login-password-error" class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'auth.passwordRequired' | t }}</span>
              }
              <span class="mt-2 block text-right">
                <a routerLink="/forgot-password" queryParamsHandling="preserve" class="premium-link text-xs">{{ 'auth.forgotLink' | t }}</a>
              </span>
            </label>

            @if (error()) {
              <app-state-panel mode="error" title="{{ 'auth.login.failed' | t }}" [message]="error()!" />
            }

            <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="form.invalid || loading()">
              <lucide-icon [img]="ShieldCheck" size="18" />
            {{ loading() ? ('auth.login.loading' | t) : ('auth.login.submit' | t) }}
            </button>
          </form>

          <p class="mt-6 text-center text-sm text-aurora-muted dark:text-stone-300">
            {{ 'auth.new' | t }}
            <a routerLink="/register" queryParamsHandling="preserve" class="premium-link">{{ 'auth.create' | t }}</a>
          </p>
        </div>
        }

        <a routerLink="/catalog" class="mt-5 flex cursor-pointer items-center justify-center gap-2 text-sm font-bold text-aurora-muted transition-colors duration-200 hover:text-aurora-gold dark:text-stone-300">
          {{ 'auth.continue' | t }}
          <lucide-icon [img]="ArrowRight" size="16" />
        </a>
      </div>
    </section>
  `
})
export class LoginPageComponent {
  private readonly formBuilder = inject(FormBuilder);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  // When the login response is an MFA challenge, the page swaps to the code-entry step.
  readonly mfaRequired = signal(false);
  readonly ArrowRight = ArrowRight;
  readonly Mail = Mail;
  readonly LockKeyhole = LockKeyhole;
  readonly KeyRound = KeyRound;
  readonly ShieldCheck = ShieldCheck;
  readonly Sparkles = Sparkles;
  readonly panelItems = ['auth.benefit.favorites', 'auth.benefit.checkout', 'auth.benefit.orders'];

  // The opaque challenge token returned by /login; consumed by /mfa/verify. Single-use server-side.
  private mfaToken: string | null = null;

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  readonly mfaForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
  });

  constructor(
    private readonly authService: AuthService,
    private readonly language: LanguageService,
    private readonly router: Router,
    private readonly route: ActivatedRoute
  ) {}

  emailInvalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && (control.dirty || control.touched);
  }

  passwordInvalid(): boolean {
    const control = this.form.controls.password;
    return control.invalid && (control.dirty || control.touched);
  }

  mfaCodeInvalid(): boolean {
    const control = this.mfaForm.controls.code;
    return control.invalid && (control.dirty || control.touched);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.authService.login(this.form.getRawValue()).subscribe({
      next: (response: ApiResponse<AuthPayload>) => {
        // MFA-enrolled user: no session was established — switch to the code step instead of navigating.
        if (response.data?.status === 'MFA_REQUIRED' && response.data.mfaToken) {
          this.mfaToken = response.data.mfaToken;
          this.mfaRequired.set(true);
          this.loading.set(false);
          return;
        }
        this.navigateAfterAuth();
      },
      error: () => {
        this.error.set(this.language.translate('auth.login.error'));
        this.loading.set(false);
      }
    });
  }

  submitMfa(): void {
    if (this.mfaForm.invalid || !this.mfaToken) {
      this.mfaForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.authService.mfaVerify(this.mfaToken, this.mfaForm.getRawValue().code).subscribe({
      next: () => this.navigateAfterAuth(),
      error: (err: { status?: number }) => {
        // A 401 means the challenge is consumed/expired — a fresh login is required.
        const key = err?.status === 401 ? 'mfa.challenge.expired' : 'mfa.challenge.error';
        this.error.set(this.language.translate(key));
        this.loading.set(false);
      }
    });
  }

  /** Abandons the MFA step and returns to the credentials form (e.g. challenge consumed). */
  backToLogin(): void {
    this.mfaRequired.set(false);
    this.mfaToken = null;
    this.mfaForm.reset();
    this.form.controls.password.reset();
    this.error.set(null);
    this.loading.set(false);
  }

  /** Honors the same sanitized returnUrl logic the normal sign-in uses (open-redirect defense). */
  private navigateAfterAuth(): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    this.router.navigateByUrl(safeInternalUrl(returnUrl));
  }
}
