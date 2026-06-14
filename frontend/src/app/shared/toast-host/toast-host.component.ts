import { Component, inject } from '@angular/core';
import { LucideAngularModule, AlertCircle, CheckCircle2, Info, X } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-toast-host',
  imports: [LucideAngularModule, TranslatePipe],
  template: `
    <div
      class="pointer-events-none fixed inset-x-0 bottom-0 z-[60] flex flex-col items-center gap-2 p-4 sm:inset-x-auto sm:right-4 sm:items-end"
      aria-live="polite"
      aria-atomic="false"
    >
      @for (toast of toasts(); track toast.id) {
        <div
          role="status"
          class="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-ui border bg-white p-4 shadow-premium animate-fadeUp dark:bg-aurora-night"
          [class.border-aurora-line]="toast.tone === 'info'"
          [class.border-emerald-200]="toast.tone === 'success'"
          [class.border-rose-200]="toast.tone === 'error'"
          [class.dark:border-white/10]="true"
        >
          <span class="mt-0.5 shrink-0">
            @if (toast.tone === 'success') {
              <lucide-icon class="text-aurora-emerald" [img]="CheckCircle2" size="20" />
            } @else if (toast.tone === 'error') {
              <lucide-icon class="text-aurora-rose" [img]="AlertCircle" size="20" />
            } @else {
              <lucide-icon class="text-aurora-gold" [img]="Info" size="20" />
            }
          </span>
          <p class="flex-1 text-sm font-bold leading-5 text-aurora-ink dark:text-white">{{ toast.message }}</p>
          <button
            class="shrink-0 cursor-pointer rounded-ui p-1 text-aurora-muted transition-colors duration-200 hover:text-aurora-ink dark:text-stone-400 dark:hover:text-white"
            type="button"
            [attr.aria-label]="'common.close' | t"
            (click)="toastService.dismiss(toast.id)"
          >
            <lucide-icon [img]="X" size="16" />
          </button>
        </div>
      }
    </div>
  `
})
export class ToastHostComponent {
  protected readonly toastService = inject(ToastService);
  readonly toasts = this.toastService.toasts;

  readonly CheckCircle2 = CheckCircle2;
  readonly AlertCircle = AlertCircle;
  readonly Info = Info;
  readonly X = X;
}
