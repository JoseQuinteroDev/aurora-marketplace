import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowRight, CheckCircle2, LockKeyhole, Mail, UserPlus, UserRound } from 'lucide-angular';
import { LanguageService } from '../../core/i18n/language.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { safeInternalUrl } from '../../core/util/safe-internal-url';
import { AuthService } from '../../services/auth.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell grid min-h-[800px] items-center gap-10 py-10 lg:grid-cols-[1.05fr_0.95fr]">
      <div class="mx-auto w-full max-w-xl">
        <div class="surface-panel p-6 sm:p-8">
          <p class="section-kicker">{{ 'auth.create' | t }}</p>
          <h1 class="mt-3 text-4xl font-semibold text-aurora-ink dark:text-white">{{ 'auth.register.title' | t }}</h1>
          <p class="mt-3 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'auth.register.subtitle' | t }}</p>

          <form class="mt-8 grid gap-4" [formGroup]="form" (ngSubmit)="submit()">
            <div class="grid gap-4 sm:grid-cols-2">
              <label class="block">
                <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'auth.firstName' | t }}</span>
                <span class="field-shell" [class.field-shell-invalid]="controlInvalid('firstName')">
                  <lucide-icon class="text-stone-400" [img]="UserRound" size="17" />
                  <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="firstName" autocomplete="given-name" [placeholder]="'auth.placeholder.firstName' | t" />
                </span>
                @if (controlInvalid('firstName')) {
                  <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'auth.firstNameRequired' | t }}</span>
                }
              </label>

              <label class="block">
                <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'auth.lastName' | t }}</span>
                <span class="field-shell" [class.field-shell-invalid]="controlInvalid('lastName')">
                  <lucide-icon class="text-stone-400" [img]="UserRound" size="17" />
                  <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="lastName" autocomplete="family-name" [placeholder]="'auth.placeholder.lastName' | t" />
                </span>
                @if (controlInvalid('lastName')) {
                  <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'auth.lastNameRequired' | t }}</span>
                }
              </label>
            </div>

            <label class="block">
              <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'auth.email' | t }}</span>
              <span class="field-shell" [class.field-shell-invalid]="controlInvalid('email')">
                <lucide-icon class="text-stone-400" [img]="Mail" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="email" type="email" autocomplete="email" [placeholder]="'auth.placeholder.email' | t" />
              </span>
              @if (controlInvalid('email')) {
                <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'auth.emailInvalid' | t }}</span>
              }
            </label>

            <label class="block">
              <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'auth.password' | t }}</span>
              <span class="field-shell" [class.field-shell-invalid]="controlInvalid('password')">
                <lucide-icon class="text-stone-400" [img]="LockKeyhole" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="password" type="password" autocomplete="new-password" [placeholder]="'auth.placeholder.passwordNew' | t" />
              </span>
              @if (controlInvalid('password')) {
                <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'auth.passwordLength' | t }}</span>
              }
            </label>

            @if (error()) {
              <app-state-panel mode="error" title="{{ 'auth.register.failed' | t }}" [message]="error()!" />
            }

            <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="form.invalid || loading()">
              <lucide-icon [img]="UserPlus" size="18" />
              {{ loading() ? ('auth.register.loading' | t) : ('auth.register.submit' | t) }}
            </button>
          </form>

          <p class="mt-6 text-center text-sm text-aurora-muted dark:text-stone-300">
            {{ 'auth.exists' | t }}
            <a routerLink="/login" class="premium-link">{{ 'auth.signIn' | t }}</a>
          </p>
        </div>
      </div>

      <div class="relative hidden min-h-[700px] overflow-hidden rounded-ui bg-aurora-night shadow-premium lg:block">
        <img class="absolute inset-0 h-full w-full object-cover opacity-75" src="https://images.unsplash.com/photo-1556742502-ec7c0e9f34b1?auto=format&fit=crop&w=1400&q=85" alt="Crear una cuenta en Aurora" />
        <div class="absolute inset-0 bg-gradient-to-t from-aurora-night via-aurora-night/40 to-transparent"></div>
        <div class="absolute bottom-0 left-0 right-0 p-8 text-white">
          <h2 class="max-w-md text-4xl font-black leading-tight">{{ 'auth.register.title' | t }}</h2>
          <div class="mt-6 grid gap-3">
            @for (item of panelItems; track item) {
              <div class="flex items-center gap-3 rounded-ui border border-white/10 bg-white/10 p-3">
                <lucide-icon class="text-emerald-300" [img]="CheckCircle2" size="18" />
                <span class="text-sm font-semibold text-stone-100">{{ item | t }}</span>
              </div>
            }
          </div>
          <a routerLink="/catalog" class="mt-7 inline-flex cursor-pointer items-center gap-2 text-sm font-black text-amber-200 transition-colors duration-200 hover:text-white">
            {{ 'cart.keepShopping' | t }}
            <lucide-icon [img]="ArrowRight" size="16" />
          </a>
        </div>
      </div>
    </section>
  `
})
export class RegisterPageComponent {
  private readonly formBuilder = inject(FormBuilder);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly ArrowRight = ArrowRight;
  readonly CheckCircle2 = CheckCircle2;
  readonly Mail = Mail;
  readonly LockKeyhole = LockKeyhole;
  readonly UserPlus = UserPlus;
  readonly UserRound = UserRound;
  readonly panelItems = ['auth.benefit.favorites', 'auth.benefit.checkout', 'auth.benefit.orders'];

  readonly form = this.formBuilder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]]
  });

  constructor(
    private readonly authService: AuthService,
    private readonly language: LanguageService,
    private readonly router: Router,
    private readonly route: ActivatedRoute
  ) {}

  controlInvalid(controlName: 'firstName' | 'lastName' | 'email' | 'password'): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.dirty || control.touched);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.authService.register(this.form.getRawValue()).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        this.router.navigateByUrl(safeInternalUrl(returnUrl));
      },
      error: () => {
        this.error.set(this.language.translate('auth.register.error'));
        this.loading.set(false);
      }
    });
  }
}
