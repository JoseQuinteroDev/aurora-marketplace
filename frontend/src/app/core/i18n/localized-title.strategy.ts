import { Injectable, effect, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { LanguageService } from './language.service';

const BRAND = 'Aurora Marketplace';

/**
 * Resolves each route's `title` as a translation KEY and localizes the browser
 * tab title, re-applying it when the language changes mid-session.
 */
@Injectable({ providedIn: 'root' })
export class LocalizedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly language = inject(LanguageService);
  private currentKey: string | null = null;

  constructor() {
    super();
    effect(() => {
      this.language.language(); // track language changes
      this.apply();
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.currentKey = this.buildTitle(snapshot) ?? null;
    this.apply();
  }

  private apply(): void {
    if (!this.currentKey || this.currentKey === 'title.home') {
      this.title.setTitle(BRAND);
      return;
    }

    this.title.setTitle(`${this.language.translate(this.currentKey)} · ${BRAND}`);
  }
}
