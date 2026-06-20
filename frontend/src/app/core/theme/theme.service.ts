import { Injectable, effect, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'aurora-theme';

/**
 * Owns the light/dark theme. The `.dark` class on <html> is what activates every
 * Tailwind `dark:` variant across the storefront. An inline script in index.html
 * applies the stored/preferred theme before Angular boots (no flash); this service
 * keeps it in sync at runtime and persists the user's explicit choice.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<ThemeMode>(this.readInitial());

  constructor() {
    // The effect only reflects the theme in the DOM. Persistence happens on an
    // explicit toggle (below), so a first-time visitor's OS preference stays the
    // live default until they actually choose a theme.
    effect(() => {
      const mode = this.theme();
      if (typeof document === 'undefined') {
        return;
      }
      document.documentElement.classList.toggle('dark', mode === 'dark');
    });
  }

  isDark(): boolean {
    return this.theme() === 'dark';
  }

  toggle(): void {
    this.theme.update((mode) => (mode === 'dark' ? 'light' : 'dark'));
    try {
      localStorage.setItem(STORAGE_KEY, this.theme());
    } catch {
      // Private mode / storage disabled — theme still applies for this session.
    }
  }

  private readInitial(): ThemeMode {
    if (typeof window === 'undefined') {
      return 'light';
    }
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'light' || stored === 'dark') {
        return stored;
      }
    } catch {
      // ignore and fall back to the OS preference
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
}
