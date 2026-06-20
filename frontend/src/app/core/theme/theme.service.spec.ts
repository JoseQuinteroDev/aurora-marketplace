import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
  });

  it('reads the initial theme from localStorage', () => {
    localStorage.setItem('aurora-theme', 'dark');
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(true);
  });

  it('toggle() flips the theme and persists the explicit choice', () => {
    localStorage.setItem('aurora-theme', 'light');
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(false);

    service.toggle();
    expect(service.isDark()).toBe(true);
    expect(localStorage.getItem('aurora-theme')).toBe('dark');

    service.toggle();
    expect(service.isDark()).toBe(false);
    expect(localStorage.getItem('aurora-theme')).toBe('light');
  });

  it('reflects the theme on the document root element', () => {
    localStorage.setItem('aurora-theme', 'light');
    const service = TestBed.inject(ThemeService);

    service.toggle();
    TestBed.tick();
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    service.toggle();
    TestBed.tick();
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });
});
