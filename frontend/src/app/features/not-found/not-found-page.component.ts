import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowRight, Compass } from 'lucide-angular';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink, LucideAngularModule, TranslatePipe],
  template: `
    <section class="page-shell flex min-h-[62vh] flex-col items-center justify-center py-20 text-center">
      <span class="flex h-14 w-14 items-center justify-center rounded-ui bg-aurora-ink text-white shadow-lift dark:bg-white dark:text-aurora-night">
        <lucide-icon [img]="Compass" size="26" />
      </span>
      <p class="section-kicker mt-6">404</p>
      <h1 class="mt-3 text-5xl font-semibold tracking-tight text-aurora-ink sm:text-6xl dark:text-white">{{ 'notFound.title' | t }}</h1>
      <p class="mt-4 max-w-md text-sm leading-6 text-aurora-muted dark:text-stone-300">{{ 'notFound.message' | t }}</p>
      <div class="mt-8 flex flex-col gap-3 sm:flex-row">
        <a routerLink="/" class="ui-button ui-button-primary">
          {{ 'notFound.home' | t }}
          <lucide-icon [img]="ArrowRight" size="18" />
        </a>
        <a routerLink="/catalog" class="ui-button ui-button-secondary">{{ 'nav.catalog' | t }}</a>
      </div>
    </section>
  `
})
export class NotFoundPageComponent {
  readonly ArrowRight = ArrowRight;
  readonly Compass = Compass;
}
