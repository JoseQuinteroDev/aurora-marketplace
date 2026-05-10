import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowRight, LockKeyhole, Mail, ShieldCheck, Sparkles } from 'lucide-angular';
import { AuthService } from '../../services/auth.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, StatePanelComponent],
  template: `
    <section class="page-shell grid min-h-[760px] items-center gap-10 py-10 lg:grid-cols-[0.96fr_1.04fr]">
      <div class="relative hidden min-h-[660px] overflow-hidden rounded-ui bg-aurora-night shadow-premium lg:block">
        <img class="absolute inset-0 h-full w-full object-cover opacity-75" src="https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?auto=format&fit=crop&w=1400&q=85" alt="Premium commerce checkout workspace" />
        <div class="absolute inset-0 bg-gradient-to-t from-aurora-night via-aurora-night/40 to-transparent"></div>
        <div class="absolute bottom-0 left-0 right-0 p-8 text-white">
          <div class="inline-flex items-center gap-2 rounded-ui border border-white/15 bg-white/10 px-3 py-2 text-xs font-black uppercase tracking-[0.16em] text-amber-200">
            <lucide-icon [img]="Sparkles" size="14" />
            Member access
          </div>
          <h1 class="mt-5 max-w-md text-4xl font-black leading-tight">Pick up your cart, favorites and orders in one calm place.</h1>
          <div class="mt-6 grid gap-3">
            @for (item of panelItems; track item) {
              <div class="flex items-center gap-3 rounded-ui border border-white/10 bg-white/10 p-3">
                <lucide-icon class="text-amber-300" [img]="ShieldCheck" size="18" />
                <span class="text-sm font-semibold text-stone-100">{{ item }}</span>
              </div>
            }
          </div>
        </div>
      </div>

      <div class="mx-auto w-full max-w-md">
        <div class="surface-panel p-6 sm:p-8">
          <p class="section-kicker">Welcome back</p>
          <h1 class="mt-3 text-4xl font-black text-aurora-ink dark:text-white">Sign in to Aurora</h1>
          <p class="mt-3 text-sm leading-6 text-aurora-muted dark:text-stone-300">Access your saved items, carts and admin tools when your role allows it.</p>

          <form class="mt-8 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
            <label class="block">
              <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">Email</span>
              <span class="field-shell" [class.field-shell-invalid]="emailInvalid()">
                <lucide-icon class="text-stone-400" [img]="Mail" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="email" type="email" autocomplete="email" placeholder="you@aurora.dev" />
              </span>
              @if (emailInvalid()) {
                <span class="mt-2 block text-xs font-bold text-aurora-rose">Enter a valid email address.</span>
              }
            </label>

            <label class="block">
              <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">Password</span>
              <span class="field-shell" [class.field-shell-invalid]="passwordInvalid()">
                <lucide-icon class="text-stone-400" [img]="LockKeyhole" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="password" type="password" autocomplete="current-password" placeholder="Your password" />
              </span>
              @if (passwordInvalid()) {
                <span class="mt-2 block text-xs font-bold text-aurora-rose">Password is required.</span>
              }
            </label>

            @if (error()) {
              <app-state-panel mode="error" title="Login failed" [message]="error()!" />
            }

            <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="form.invalid || loading()">
              <lucide-icon [img]="ShieldCheck" size="18" />
              {{ loading() ? 'Signing in...' : 'Sign in securely' }}
            </button>
          </form>

          <p class="mt-6 text-center text-sm text-aurora-muted dark:text-stone-300">
            New to Aurora?
            <a routerLink="/register" class="premium-link">Create an account</a>
          </p>
        </div>

        <a routerLink="/catalog" class="mt-5 flex cursor-pointer items-center justify-center gap-2 text-sm font-bold text-aurora-muted transition-colors duration-200 hover:text-aurora-gold dark:text-stone-300">
          Continue browsing
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
  readonly ArrowRight = ArrowRight;
  readonly Mail = Mail;
  readonly LockKeyhole = LockKeyhole;
  readonly ShieldCheck = ShieldCheck;
  readonly Sparkles = Sparkles;
  readonly panelItems = ['Role-aware access', 'Saved carts and wishlist', 'Admin shell when available'];

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  emailInvalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && (control.dirty || control.touched);
  }

  passwordInvalid(): boolean {
    const control = this.form.controls.password;
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
      next: () => this.router.navigateByUrl('/'),
      error: () => {
        this.error.set('Check your email and password, then try again.');
        this.loading.set(false);
      }
    });
  }
}
