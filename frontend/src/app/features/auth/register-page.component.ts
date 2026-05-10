import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowRight, CheckCircle2, LockKeyhole, Mail, UserPlus, UserRound } from 'lucide-angular';
import { AuthService } from '../../services/auth.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, StatePanelComponent],
  template: `
    <section class="page-shell grid min-h-[800px] items-center gap-10 py-10 lg:grid-cols-[1.05fr_0.95fr]">
      <div class="mx-auto w-full max-w-xl">
        <div class="surface-panel p-6 sm:p-8">
          <p class="section-kicker">Join Aurora</p>
          <h1 class="mt-3 text-4xl font-black text-aurora-ink dark:text-white">Create your marketplace account</h1>
          <p class="mt-3 text-sm leading-6 text-aurora-muted dark:text-stone-300">Start with a clean account flow prepared for cart, wishlist and order journeys.</p>

          <form class="mt-8 grid gap-4" [formGroup]="form" (ngSubmit)="submit()">
            <div class="grid gap-4 sm:grid-cols-2">
              <label class="block">
                <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">First name</span>
                <span class="field-shell" [class.field-shell-invalid]="controlInvalid('firstName')">
                  <lucide-icon class="text-stone-400" [img]="UserRound" size="17" />
                  <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="firstName" autocomplete="given-name" placeholder="Ada" />
                </span>
                @if (controlInvalid('firstName')) {
                  <span class="mt-2 block text-xs font-bold text-aurora-rose">First name is required.</span>
                }
              </label>

              <label class="block">
                <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">Last name</span>
                <span class="field-shell" [class.field-shell-invalid]="controlInvalid('lastName')">
                  <lucide-icon class="text-stone-400" [img]="UserRound" size="17" />
                  <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="lastName" autocomplete="family-name" placeholder="Lovelace" />
                </span>
                @if (controlInvalid('lastName')) {
                  <span class="mt-2 block text-xs font-bold text-aurora-rose">Last name is required.</span>
                }
              </label>
            </div>

            <label class="block">
              <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">Email</span>
              <span class="field-shell" [class.field-shell-invalid]="controlInvalid('email')">
                <lucide-icon class="text-stone-400" [img]="Mail" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="email" type="email" autocomplete="email" placeholder="you@aurora.dev" />
              </span>
              @if (controlInvalid('email')) {
                <span class="mt-2 block text-xs font-bold text-aurora-rose">Use a valid email address.</span>
              }
            </label>

            <label class="block">
              <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">Password</span>
              <span class="field-shell" [class.field-shell-invalid]="controlInvalid('password')">
                <lucide-icon class="text-stone-400" [img]="LockKeyhole" size="17" />
                <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="password" type="password" autocomplete="new-password" placeholder="Minimum 8 characters" />
              </span>
              @if (controlInvalid('password')) {
                <span class="mt-2 block text-xs font-bold text-aurora-rose">Use 8 to 72 characters.</span>
              }
            </label>

            @if (error()) {
              <app-state-panel mode="error" title="Registration failed" [message]="error()!" />
            }

            <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="form.invalid || loading()">
              <lucide-icon [img]="UserPlus" size="18" />
              {{ loading() ? 'Creating account...' : 'Create account' }}
            </button>
          </form>

          <p class="mt-6 text-center text-sm text-aurora-muted dark:text-stone-300">
            Already have an account?
            <a routerLink="/login" class="premium-link">Sign in</a>
          </p>
        </div>
      </div>

      <div class="relative hidden min-h-[700px] overflow-hidden rounded-ui bg-aurora-night shadow-premium lg:block">
        <img class="absolute inset-0 h-full w-full object-cover opacity-75" src="https://images.unsplash.com/photo-1556742502-ec7c0e9f34b1?auto=format&fit=crop&w=1400&q=85" alt="Premium online marketplace account experience" />
        <div class="absolute inset-0 bg-gradient-to-t from-aurora-night via-aurora-night/40 to-transparent"></div>
        <div class="absolute bottom-0 left-0 right-0 p-8 text-white">
          <h2 class="max-w-md text-4xl font-black leading-tight">Your Aurora profile becomes the center of the shopping flow.</h2>
          <div class="mt-6 grid gap-3">
            @for (item of panelItems; track item) {
              <div class="flex items-center gap-3 rounded-ui border border-white/10 bg-white/10 p-3">
                <lucide-icon class="text-emerald-300" [img]="CheckCircle2" size="18" />
                <span class="text-sm font-semibold text-stone-100">{{ item }}</span>
              </div>
            }
          </div>
          <a routerLink="/catalog" class="mt-7 inline-flex cursor-pointer items-center gap-2 text-sm font-black text-amber-200 transition-colors duration-200 hover:text-white">
            Browse first
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
  readonly panelItems = ['Saved products and wishlist', 'Cart ready for checkout', 'Order history prepared'];

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
      next: () => this.router.navigateByUrl('/'),
      error: () => {
        this.error.set('The account could not be created. The email may already exist.');
        this.loading.set(false);
      }
    });
  }
}
