import { Component, input } from '@angular/core';
import { LucideAngularModule, AlertCircle, CheckCircle2, PackageOpen } from 'lucide-angular';

@Component({
  selector: 'app-state-panel',
  imports: [LucideAngularModule],
  template: `
    <div class="state-surface">
      <div class="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-ui border border-aurora-line bg-white text-aurora-charcoal shadow-sm dark:border-white/10 dark:bg-white/10 dark:text-white">
        @if (mode() === 'error') {
          <lucide-icon class="text-aurora-rose" [img]="AlertCircle" size="20" />
        } @else if (mode() === 'success') {
          <lucide-icon class="text-aurora-emerald" [img]="CheckCircle2" size="20" />
        } @else {
          <lucide-icon class="text-aurora-gold" [img]="PackageOpen" size="20" />
        }
      </div>
      <p class="font-extrabold text-aurora-ink dark:text-white">{{ title() }}</p>
      <p class="mx-auto mt-1 max-w-md leading-6">{{ message() }}</p>
    </div>
  `
})
export class StatePanelComponent {
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly mode = input<'empty' | 'error' | 'success'>('empty');

  readonly AlertCircle = AlertCircle;
  readonly CheckCircle2 = CheckCircle2;
  readonly PackageOpen = PackageOpen;
}
