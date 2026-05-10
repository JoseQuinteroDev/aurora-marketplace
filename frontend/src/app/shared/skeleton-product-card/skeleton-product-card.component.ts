import { Component } from '@angular/core';

@Component({
  selector: 'app-skeleton-product-card',
  template: `
    <div class="soft-card overflow-hidden">
      <div class="skeleton-line aspect-[4/3] rounded-none"></div>
      <div class="space-y-3 p-4">
        <div class="skeleton-line h-3 w-24"></div>
        <div class="skeleton-line h-5 w-full"></div>
        <div class="skeleton-line h-4 w-4/5"></div>
        <div class="flex items-center justify-between pt-3">
          <div class="skeleton-line h-7 w-24"></div>
          <div class="skeleton-line h-10 w-24"></div>
        </div>
      </div>
    </div>
  `
})
export class SkeletonProductCardComponent {}
