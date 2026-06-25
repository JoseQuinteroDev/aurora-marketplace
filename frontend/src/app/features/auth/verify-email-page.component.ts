import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule, ShieldCheck } from 'lucide-angular';
import { LanguageService } from '../../core/i18n/language.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { AuthService } from '../../services/auth.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

@Component({
  selector: 'app-verify-email-page',
  imports: [RouterLink, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell flex min-h-[600px] items-center justify-center py-10">
      <div class="mx-auto w-full max-w-md">
        <div class="surface-panel p-6 sm:p-8">
          <p class="section-kicker">{{ 'nav.signIn' | t }}</p>
          <h1 class="mt-3 text-4xl font-semibold text-aurora-ink dark:text-white">{{ 'auth.verify.title' | t }}</h1>
          <p class="mt-3 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'auth.verify.subtitle' | t }}</p>

          @if (error()) {
            <div class="mt-8"><app-state-panel mode="error" title="{{ 'auth.verify.title' | t }}" [message]="error()!" /></div>
            <p class="mt-6 text-center text-sm text-aurora-muted dark:text-stone-300">
              <a routerLink="/login" class="premium-link">{{ 'auth.backToSignIn' | t }}</a>
            </p>
          } @else if (!token) {
            <div class="mt-8"><app-state-panel mode="error" title="{{ 'auth.verify.title' | t }}" [message]="'auth.verify.invalid' | t" /></div>
            <p class="mt-6 text-center text-sm text-aurora-muted dark:text-stone-300">
              <a routerLink="/login" class="premium-link">{{ 'auth.backToSignIn' | t }}</a>
            </p>
          } @else if (done()) {
            <div class="mt-8"><app-state-panel mode="success" title="{{ 'auth.verify.successTitle' | t }}" [message]="'auth.verify.success' | t" /></div>
            <p class="mt-6 text-center text-sm text-aurora-muted dark:text-stone-300">
              <a routerLink="/login" class="premium-link">{{ 'auth.backToSignIn' | t }}</a>
            </p>
          } @else {
            <button class="ui-button ui-button-primary mt-8 w-full" type="button" (click)="verify()" [disabled]="verifying()">
              <lucide-icon [img]="ShieldCheck" size="18" />
              {{ verifying() ? ('auth.verify.loading' | t) : ('auth.verify.cta' | t) }}
            </button>
          }
        </div>
      </div>
    </section>
  `
})
export class VerifyEmailPageComponent {
  private readonly route = inject(ActivatedRoute);

  // Read once; verification is consumed only on an explicit click (NOT in a lifecycle hook),
  // so a mail-scanner / SafeLinks GET-prefetch can't burn the single-use token.
  readonly token = this.route.snapshot.queryParamMap.get('token');
  readonly verifying = signal(false);
  readonly done = signal(false);
  readonly error = signal<string | null>(null);
  readonly ShieldCheck = ShieldCheck;

  constructor(
    private readonly authService: AuthService,
    private readonly language: LanguageService
  ) {}

  verify(): void {
    if (!this.token) {
      return;
    }
    this.verifying.set(true);
    this.error.set(null);
    this.authService.verifyEmail(this.token).subscribe({
      next: () => {
        // Refresh the session so emailVerified flips and the banner clears (best-effort:
        // a no-op when the link is opened in a logged-out browser).
        this.authService.refresh().subscribe({ next: () => {}, error: () => {} });
        this.done.set(true);
        this.verifying.set(false);
      },
      error: (err: { status?: number }) => {
        this.error.set(this.language.translate(
          err?.status === 401 || err?.status === 400 ? 'auth.verify.invalid' : 'auth.verify.error'));
        this.verifying.set(false);
      }
    });
  }
}
