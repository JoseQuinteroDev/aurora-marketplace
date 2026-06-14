import { Injectable, computed, signal } from '@angular/core';
import { LanguageCode, translations } from './translations';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly storageKey = 'aurora_language';
  private readonly languageSignal = signal<LanguageCode>(this.loadLanguage());

  readonly language = this.languageSignal.asReadonly();
  readonly isSpanish = computed(() => this.languageSignal() === 'es');

  constructor() {
    this.applyDocumentLang(this.languageSignal());
  }

  setLanguage(language: LanguageCode): void {
    this.languageSignal.set(language);
    localStorage.setItem(this.storageKey, language);
    this.applyDocumentLang(language);
  }

  toggle(): void {
    this.setLanguage(this.languageSignal() === 'es' ? 'en' : 'es');
  }

  translate(key: string, params?: Record<string, string | number>): string {
    const value = translations[this.languageSignal()][key] ?? translations.en[key] ?? key;

    if (!params) {
      return value;
    }

    return Object.entries(params).reduce(
      (text, [paramKey, paramValue]) => text.replaceAll(`{${paramKey}}`, String(paramValue)),
      value
    );
  }

  private loadLanguage(): LanguageCode {
    const stored = localStorage.getItem(this.storageKey);
    return stored === 'en' || stored === 'es' ? stored : 'es';
  }

  private applyDocumentLang(language: LanguageCode): void {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = language;
    }
  }
}
