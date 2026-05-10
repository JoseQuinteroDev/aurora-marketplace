import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule, LockKeyhole, Mail, UserPlus, UserRound } from 'lucide-angular';
import { AuthService } from '../../services/auth.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, StatePanelComponent],
  template: `
    <section class="page-shell grid min-h-[760px] items-center gap-10 py-10 lg:grid-cols-[1.05fr_0.95fr]">
      <div class="mx-auto w-full max-w-lg">
        <p class="section-kicker">Join Aurora</p>
        <h1 class="mt-3 text-4xl font-black text-slate-950 dark:text-white">Create your marketplace account</h1>
        <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">A polished account flow wired to the register API with JWT persistence in localStorage for the MVP.</p>

        <form class="mt-8 grid gap-4" [formGroup]="form" (ngSubmit)="submit()">
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="block">
              <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">First name</span>
              <span class="mt-2 flex items-center gap-2 rounded-ui border border-slate-200 bg-white px-3 dark:border-white/10 dark:bg-white/10">
                <lucide-icon class="text-slate-400" [img]="UserRound" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="firstName" placeholder="Ada" />
              </span>
            </label>
            <label class="block">
              <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">Last name</span>
              <span class="mt-2 flex items-center gap-2 rounded-ui border border-slate-200 bg-white px-3 dark:border-white/10 dark:bg-white/10">
                <lucide-icon class="text-slate-400" [img]="UserRound" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="lastName" placeholder="Lovelace" />
              </span>
            </label>
          </div>

          <label class="block">
            <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">Email</span>
            <span class="mt-2 flex items-center gap-2 rounded-ui border border-slate-200 bg-white px-3 dark:border-white/10 dark:bg-white/10">
              <lucide-icon class="text-slate-400" [img]="Mail" size="17" />
              <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="email" type="email" autocomplete="email" placeholder="you@aurora.dev" />
            </span>
          </label>

          <label class="block">
            <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">Password</span>
            <span class="mt-2 flex items-center gap-2 rounded-ui border border-slate-200 bg-white px-3 dark:border-white/10 dark:bg-white/10">
              <lucide-icon class="text-slate-400" [img]="LockKeyhole" size="17" />
              <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="password" type="password" autocomplete="new-password" placeholder="Minimum 8 characters" />
            </span>
          </label>

          @if (error()) {
            <app-state-panel mode="error" title="Registration failed" [message]="error()!" />
          }

          <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="form.invalid || loading()">
            <lucide-icon [img]="UserPlus" size="18" />
            {{ loading() ? 'Creating account...' : 'Create account' }}
          </button>
        </form>

        <p class="mt-6 text-center text-sm text-slate-600 dark:text-slate-300">
          Already have an account?
          <a routerLink="/login" class="cursor-pointer font-bold text-aurora-ocean hover:text-blue-700">Sign in</a>
        </p>
      </div>

      <div class="hidden overflow-hidden rounded-ui border border-slate-200 bg-white shadow-premium lg:block dark:border-white/10 dark:bg-white/[0.06]">
        <img class="h-[680px] w-full object-cover" src="https://images.unsplash.com/photo-1556742502-ec7c0e9f34b1?auto=format&fit=crop&w=1200&q=85" alt="Premium online marketplace account experience" />
      </div>
    </section>
  `
})
export class RegisterPageComponent {
  private readonly formBuilder = inject(FormBuilder);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly Mail = Mail;
  readonly LockKeyhole = LockKeyhole;
  readonly UserPlus = UserPlus;
  readonly UserRound = UserRound;

  readonly form = this.formBuilder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]]
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
    this.authService.register(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: () => {
        this.error.set('The account could not be created. The email may already exist.');
        this.loading.set(false);
      }
    });
  }
}
