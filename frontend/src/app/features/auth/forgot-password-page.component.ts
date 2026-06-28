import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, Mail } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { AuthService } from '../../services/auth.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-forgot-password-page',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell flex min-h-[600px] items-center justify-center py-10">
      <div class="mx-auto w-full max-w-md">
        <div class="surface-panel p-6 sm:p-8">
          <p class="section-kicker">{{ 'nav.signIn' | t }}</p>
          <h1 class="mt-3 text-4xl font-semibold text-aurora-ink dark:text-white">{{ 'auth.forgot.title' | t }}</h1>
          <p class="mt-3 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'auth.forgot.subtitle' | t }}</p>

          @if (submitted()) {
            <div class="mt-8">
              <app-state-panel mode="success" title="{{ 'auth.forgot.sentTitle' | t }}" [message]="'auth.forgot.sent' | t" />
            </div>
          } @else {
            <form class="mt-8 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
              <label class="block">
                <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'auth.email' | t }}</span>
                <span class="field-shell" [class.field-shell-invalid]="emailInvalid()">
                  <lucide-icon class="text-stone-400" [img]="Mail" size="17" />
                  <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="email" type="email" autocomplete="email" [placeholder]="'auth.placeholder.email' | t" />
                </span>
                @if (emailInvalid()) {
                  <span class="mt-2 block text-xs font-bold text-aurora-rose">{{ 'auth.emailInvalid' | t }}</span>
                }
              </label>

              <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="form.invalid || loading()">
                {{ loading() ? ('auth.forgot.loading' | t) : ('auth.forgot.submit' | t) }}
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
export class ForgotPasswordPageComponent {
  private readonly formBuilder = inject(FormBuilder);

  readonly loading = signal(false);
  readonly submitted = signal(false);
  readonly Mail = Mail;

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  constructor(private readonly authService: AuthService) {}

  emailInvalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && (control.dirty || control.touched);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    // Anti-enumeration: show the same "sent" confirmation on success AND error, so the
    // UI never reveals whether the email exists (the backend always returns 200 anyway).
    this.authService.requestPasswordReset(this.form.getRawValue().email).subscribe({
      next: () => {
        this.submitted.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.submitted.set(true);
        this.loading.set(false);
      }
    });
  }
}
