import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule, LockKeyhole } from 'lucide-angular';
import { LanguageService } from '../../core/i18n/language.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

/** Group-level validator: the two password fields must match. */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('newPassword')?.value;
  const confirm = group.get('confirm')?.value;
  return password === confirm ? null : { mismatch: true };
}

@Component({
  selector: 'app-reset-password-page',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell flex min-h-[600px] items-center justify-center py-10">
      <div class="mx-auto w-full max-w-md">
        <div class="surface-panel p-6 sm:p-8">
          <p class="section-kicker">{{ 'nav.signIn' | t }}</p>
          <h1 class="mt-3 text-4xl font-semibold text-aurora-ink dark:text-white">{{ 'auth.reset.title' | t }}</h1>
          <p class="mt-3 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'auth.reset.subtitle' | t }}</p>

          @if (!token) {
            <div class="mt-8">
              <app-state-panel mode="error" title="{{ 'auth.reset.title' | t }}" [message]="'auth.reset.invalidToken' | t" />
            </div>
            <p class="mt-6 text-center text-sm text-aurora-muted dark:text-stone-300">
              <a routerLink="/forgot-password" class="premium-link">{{ 'auth.forgot.submit' | t }}</a>
            </p>
          } @else {
            <form class="mt-8 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
              <label class="block">
                <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'auth.reset.passwordLabel' | t }}</span>
                <span class="field-shell" [class.field-shell-invalid]="controlInvalid('newPassword')">
                  <lucide-icon class="text-stone-400" [img]="LockKeyhole" size="17" />
                  <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="newPassword" type="password" autocomplete="new-password" [placeholder]="'auth.placeholder.passwordNew' | t" />
                </span>
                @if (controlInvalid('newPassword')) {
                  @if (form.controls.newPassword.hasError('required')) {
                    <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'auth.passwordRequired' | t }}</span>
                  } @else {
                    <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'auth.passwordLength' | t }}</span>
                  }
                }
              </label>

              <label class="block">
                <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'auth.confirmPassword' | t }}</span>
                <span class="field-shell" [class.field-shell-invalid]="mismatch()">
                  <lucide-icon class="text-stone-400" [img]="LockKeyhole" size="17" />
                  <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="confirm" type="password" autocomplete="new-password" />
                </span>
                @if (mismatch()) {
                  <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'auth.reset.mismatch' | t }}</span>
                }
              </label>

              @if (error()) {
                <app-state-panel mode="error" title="{{ 'auth.reset.title' | t }}" [message]="error()!" />
              }

              <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="form.invalid || loading()">
                {{ loading() ? ('auth.reset.loading' | t) : ('auth.reset.submit' | t) }}
              </button>
            </form>
          }

          <p class="mt-6 text-center text-sm text-aurora-muted dark:text-stone-300">
            <a routerLink="/login" class="premium-link">{{ 'auth.backToSignIn' | t }}</a>
          </p>
        </div>
      </div>
    </section>
  `
})
export class ResetPasswordPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  readonly token = this.route.snapshot.queryParamMap.get('token');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly LockKeyhole = LockKeyhole;

  readonly form = this.formBuilder.nonNullable.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirm: ['', [Validators.required]]
    },
    { validators: [passwordsMatch] }
  );

  constructor(
    private readonly authService: AuthService,
    private readonly language: LanguageService,
    private readonly router: Router,
    private readonly toast: ToastService
  ) {}

  controlInvalid(name: 'newPassword' | 'confirm'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.dirty || control.touched);
  }

  mismatch(): boolean {
    return this.form.hasError('mismatch')
      && (this.form.controls.confirm.dirty || this.form.controls.confirm.touched);
  }

  submit(): void {
    if (!this.token || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.authService.resetPassword(this.token, this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.toast.success(this.language.translate('auth.reset.success'));
        this.router.navigateByUrl('/login');
      },
      error: (err: { status?: number }) => {
        this.error.set(this.language.translate(err?.status === 401 ? 'auth.reset.invalidToken' : 'auth.reset.error'));
        this.loading.set(false);
      }
    });
  }
}
