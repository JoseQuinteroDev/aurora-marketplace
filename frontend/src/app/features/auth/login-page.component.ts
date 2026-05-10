import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule, LockKeyhole, Mail, ShieldCheck } from 'lucide-angular';
import { AuthService } from '../../services/auth.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, StatePanelComponent],
  template: `
    <section class="page-shell grid min-h-[720px] items-center gap-10 py-10 lg:grid-cols-[0.95fr_1.05fr]">
      <div class="hidden overflow-hidden rounded-ui border border-slate-200 bg-white shadow-premium lg:block dark:border-white/10 dark:bg-white/[0.06]">
        <img class="h-[640px] w-full object-cover" src="https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?auto=format&fit=crop&w=1200&q=85" alt="Premium commerce checkout workspace" />
      </div>

      <div class="mx-auto w-full max-w-md">
        <p class="section-kicker">Welcome back</p>
        <h1 class="mt-3 text-4xl font-black text-slate-950 dark:text-white">Sign in to Aurora</h1>
        <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">Access cart, wishlist, orders and admin tools when your account has the right role.</p>

        <form class="mt-8 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
          <label class="block">
            <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">Email</span>
            <span class="mt-2 flex items-center gap-2 rounded-ui border border-slate-200 bg-white px-3 focus-within:border-aurora-ocean focus-within:ring-2 focus-within:ring-aurora-ocean/20 dark:border-white/10 dark:bg-white/10">
              <lucide-icon class="text-slate-400" [img]="Mail" size="17" />
              <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="email" type="email" autocomplete="email" placeholder="you@aurora.dev" />
            </span>
          </label>

          <label class="block">
            <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">Password</span>
            <span class="mt-2 flex items-center gap-2 rounded-ui border border-slate-200 bg-white px-3 focus-within:border-aurora-ocean focus-within:ring-2 focus-within:ring-aurora-ocean/20 dark:border-white/10 dark:bg-white/10">
              <lucide-icon class="text-slate-400" [img]="LockKeyhole" size="17" />
              <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="password" type="password" autocomplete="current-password" placeholder="Your password" />
            </span>
          </label>

          @if (error()) {
            <app-state-panel mode="error" title="Login failed" [message]="error()!" />
          }

          <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="form.invalid || loading()">
            <lucide-icon [img]="ShieldCheck" size="18" />
            {{ loading() ? 'Signing in...' : 'Sign in' }}
          </button>
        </form>

        <p class="mt-6 text-center text-sm text-slate-600 dark:text-slate-300">
          New to Aurora?
          <a routerLink="/register" class="cursor-pointer font-bold text-aurora-ocean hover:text-blue-700">Create an account</a>
        </p>
      </div>
    </section>
  `
})
export class LoginPageComponent {
  private readonly formBuilder = inject(FormBuilder);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly Mail = Mail;
  readonly LockKeyhole = LockKeyhole;
  readonly ShieldCheck = ShieldCheck;

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

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
