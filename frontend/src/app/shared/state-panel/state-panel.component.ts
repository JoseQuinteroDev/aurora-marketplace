import { Component, input } from '@angular/core';
import { LucideAngularModule, AlertCircle, CheckCircle2, PackageOpen } from 'lucide-angular';

@Component({
  selector: 'app-state-panel',
  imports: [LucideAngularModule],
  template: `
    <div class="state-surface">
      <div class="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-ui bg-slate-100 text-slate-700 dark:bg-white/10 dark:text-white">
        @if (mode() === 'error') {
          <lucide-icon [img]="AlertCircle" size="20" />
        } @else if (mode() === 'success') {
          <lucide-icon [img]="CheckCircle2" size="20" />
        } @else {
          <lucide-icon [img]="PackageOpen" size="20" />
        }
      </div>
      <p class="font-semibold text-slate-950 dark:text-white">{{ title() }}</p>
      <p class="mt-1">{{ message() }}</p>
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
