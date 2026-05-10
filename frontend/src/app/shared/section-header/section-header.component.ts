import { Component, input } from '@angular/core';

@Component({
  selector: 'app-section-header',
  template: `
    <div class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div class="max-w-2xl">
        <p class="section-kicker">{{ eyebrow() }}</p>
        <h2 class="mt-2 text-2xl font-bold tracking-normal text-slate-950 sm:text-3xl dark:text-white">
          {{ title() }}
        </h2>
        <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">
          {{ description() }}
        </p>
      </div>
      <ng-content />
    </div>
  `
})
export class SectionHeaderComponent {
  readonly eyebrow = input.required<string>();
  readonly title = input.required<string>();
  readonly description = input.required<string>();
}
