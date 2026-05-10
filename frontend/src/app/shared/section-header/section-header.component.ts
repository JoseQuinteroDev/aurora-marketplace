import { Component, input } from '@angular/core';

@Component({
  selector: 'app-section-header',
  template: `
    <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div class="max-w-2xl">
        <p class="section-kicker">{{ eyebrow() }}</p>
        <h2 class="mt-2 text-3xl font-black tracking-normal text-aurora-ink sm:text-4xl dark:text-white">
          {{ title() }}
        </h2>
        <p class="mt-3 text-sm leading-6 text-aurora-muted dark:text-stone-300">
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
