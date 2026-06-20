import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { LucideAngularModule, BellRing, Mail, Phone, Save, Smartphone } from 'lucide-angular';
import { LanguageService } from '../../core/i18n/language.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { NotificationChannel } from '../../core/models/auth.model';
import { AuthService } from '../../services/auth.service';
import { StatePanelComponent } from '../../shared/state-panel/state-panel.component';

/** A chosen SMS channel needs a phone to be deliverable - mirror the backend rule client-side. */
function smsRequiresPhone(group: AbstractControl): ValidationErrors | null {
  const channel = group.get('channel')?.value as NotificationChannel | undefined;
  const phone = String(group.get('phone')?.value ?? '').trim();
  return channel === 'SMS' && !phone ? { phoneRequiredForSms: true } : null;
}

@Component({
  selector: 'app-notification-settings-page',
  imports: [ReactiveFormsModule, LucideAngularModule, TranslatePipe, StatePanelComponent],
  template: `
    <section class="page-shell max-w-2xl py-10">
      <div class="surface-panel p-6 sm:p-8">
        <p class="section-kicker inline-flex items-center gap-2">
          <lucide-icon [img]="BellRing" size="14" />
          {{ 'settings.kicker' | t }}
        </p>
        <h1 class="mt-3 text-3xl font-semibold text-aurora-ink dark:text-white">{{ 'settings.title' | t }}</h1>
        <p class="mt-3 text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'settings.subtitle' | t }}</p>

        @if (auth.currentUser(); as user) {
          <div class="mt-6 flex items-center gap-3 rounded-ui border border-aurora-line bg-stone-50 p-3 dark:border-white/10 dark:bg-white/5">
            <lucide-icon class="text-aurora-pine dark:text-aurora-pinebright" [img]="Mail" size="18" />
            <div class="min-w-0">
              <p class="text-xs font-bold uppercase tracking-wide text-aurora-muted dark:text-stone-400">{{ 'settings.emailLabel' | t }}</p>
              <p class="truncate text-sm font-semibold text-aurora-ink dark:text-white">{{ user.email }}</p>
            </div>
          </div>

          <form class="mt-8 grid gap-6" [formGroup]="form" (ngSubmit)="submit()">
            <fieldset class="grid gap-3">
              <legend class="mb-2 text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'settings.channelLegend' | t }}</legend>

              <label class="channel-card group relative flex cursor-pointer items-start gap-3 rounded-ui border border-aurora-line p-4 transition duration-200 hover:border-aurora-pine focus-within:ring-1 focus-within:ring-aurora-pine/30 has-[:checked]:border-aurora-pine has-[:checked]:bg-aurora-pine/5 dark:border-white/10 dark:hover:border-aurora-pinebright dark:has-[:checked]:border-aurora-pinebright dark:has-[:checked]:bg-aurora-pinebright/10">
                <input class="peer sr-only" type="radio" formControlName="channel" value="EMAIL" (change)="clearBanners()" />
                <lucide-icon class="mt-0.5 text-stone-400 peer-checked:text-aurora-pine dark:peer-checked:text-aurora-pinebright" [img]="Mail" size="20" />
                <span class="min-w-0">
                  <span class="block text-sm font-bold text-aurora-ink dark:text-white">{{ 'settings.channel.email' | t }}</span>
                  <span class="mt-1 block text-xs leading-5 text-aurora-muted dark:text-stone-400">{{ 'settings.channel.emailHint' | t: { email: user.email } }}</span>
                </span>
              </label>

              <label class="channel-card group relative flex cursor-pointer items-start gap-3 rounded-ui border border-aurora-line p-4 transition duration-200 hover:border-aurora-pine focus-within:ring-1 focus-within:ring-aurora-pine/30 has-[:checked]:border-aurora-pine has-[:checked]:bg-aurora-pine/5 dark:border-white/10 dark:hover:border-aurora-pinebright dark:has-[:checked]:border-aurora-pinebright dark:has-[:checked]:bg-aurora-pinebright/10">
                <input class="peer sr-only" type="radio" formControlName="channel" value="SMS" (change)="clearBanners()" />
                <lucide-icon class="mt-0.5 text-stone-400 peer-checked:text-aurora-pine dark:peer-checked:text-aurora-pinebright" [img]="Smartphone" size="20" />
                <span class="min-w-0">
                  <span class="block text-sm font-bold text-aurora-ink dark:text-white">{{ 'settings.channel.sms' | t }}</span>
                  <span class="mt-1 block text-xs leading-5 text-aurora-muted dark:text-stone-400">{{ 'settings.channel.smsHint' | t }}</span>
                </span>
              </label>
            </fieldset>

            @if (channel() === 'SMS') {
              <label class="block">
                <span class="text-sm font-bold text-aurora-ink dark:text-stone-200">{{ 'settings.phone' | t }}</span>
                <span class="field-shell" [class.field-shell-invalid]="phoneInvalid()">
                  <lucide-icon class="text-stone-400" [img]="Phone" size="17" />
                  <input class="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none dark:text-white" formControlName="phone" type="tel" autocomplete="tel" inputmode="tel" [placeholder]="'settings.phonePlaceholder' | t" />
                </span>
                @if (phoneInvalid()) {
                  <span class="mt-2 block text-xs font-bold text-aurora-rose">
                    {{ (form.controls.phone.hasError('pattern') ? 'settings.phoneInvalid' : 'settings.phoneRequired') | t }}
                  </span>
                }
              </label>
            }

            @if (saved()) {
              <app-state-panel mode="success" title="{{ 'settings.saved' | t }}" message="{{ 'settings.savedMessage' | t }}" />
            }
            @if (error()) {
              <app-state-panel mode="error" title="{{ 'settings.error' | t }}" [message]="error()!" />
            }

            <button class="ui-button ui-button-primary w-full" type="submit" [disabled]="form.invalid || loading()">
              <lucide-icon [img]="Save" size="18" />
              {{ loading() ? ('settings.saving' | t) : ('settings.save' | t) }}
            </button>
          </form>
        }
      </div>
    </section>
  `
})
export class NotificationSettingsPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly saved = signal(false);

  readonly BellRing = BellRing;
  readonly Mail = Mail;
  readonly Phone = Phone;
  readonly Save = Save;
  readonly Smartphone = Smartphone;

  readonly form = this.formBuilder.nonNullable.group(
    {
      channel: ['EMAIL' as NotificationChannel],
      phone: ['', [Validators.maxLength(32), Validators.pattern(/^\+?[0-9][0-9 ().-]{6,30}$/)]]
    },
    { validators: [smsRequiresPhone] }
  );

  // Mirror the channel control as a signal so the template conditional reads a
  // stable value within a change-detection pass (avoids NG0100 when toggling).
  readonly channel = toSignal(this.form.controls.channel.valueChanges, {
    initialValue: this.form.controls.channel.value
  });

  constructor() {
    const user = this.auth.currentUser();
    if (user) {
      this.form.patchValue({ channel: user.notificationChannel ?? 'EMAIL', phone: user.phone ?? '' });
    }
  }

  /** A fresh edit invalidates the previous save/error banner. Driven by user events, not value streams. */
  clearBanners(): void {
    this.saved.set(false);
    this.error.set(null);
  }

  phoneInvalid(): boolean {
    const phone = this.form.controls.phone;
    const requiredForSms = this.form.hasError('phoneRequiredForSms') && (phone.dirty || phone.touched);
    return (phone.invalid && (phone.dirty || phone.touched)) || requiredForSms;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { channel, phone } = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.saved.set(false);

    this.auth.updateNotificationPreference({ channel, phone: phone.trim() || undefined }).subscribe({
      next: () => {
        this.loading.set(false);
        this.saved.set(true);
      },
      error: (err: HttpErrorResponse) => {
        const key = err?.status === 400 ? 'settings.phoneRequired' : 'settings.errorMessage';
        this.error.set(this.language.translate(key));
        this.loading.set(false);
      }
    });
  }
}
