import { Component, input } from '@angular/core';

/**
 * Aurora monogram — an "A" drawn as a dawn peak / ray, with a pine accent dot at
 * the apex, plus an optional letter-spaced wordmark. The mark uses currentColor so
 * it adapts to light/dark; only the accent dot is fixed pine.
 */
@Component({
  selector: 'app-brand-logo',
  template: `
    <span class="inline-flex items-center" [class.gap-2.5]="size() === 'md'" [class.gap-2]="size() === 'sm'">
      <svg [attr.width]="dim()" [attr.height]="dim()" viewBox="0 0 52 52" fill="none" aria-hidden="true" class="shrink-0">
        <path d="M26 7 L43 45 M26 7 L9 45 M16.5 33 H35.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
        <circle cx="26" cy="7" r="2.6" fill="#3E7C63" />
      </svg>
      @if (wordmark()) {
        <span
          class="font-display font-medium uppercase leading-none"
          [class.text-lg]="size() === 'md'"
          [class.text-sm]="size() === 'sm'"
          style="letter-spacing: 0.4em; padding-left: 0.4em"
        >Aurora</span>
      }
    </span>
  `
})
export class BrandLogoComponent {
  readonly size = input<'sm' | 'md'>('md');
  readonly wordmark = input(true);

  dim(): number {
    return this.size() === 'sm' ? 24 : 30;
  }
}
