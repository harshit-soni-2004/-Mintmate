import { Injectable, signal, effect } from '@angular/core';

export type ThemePreference = 'system' | 'light' | 'dark';
export type ActiveTheme = 'light' | 'dark';

const THEME_STORAGE_KEY = 'mintmate_theme';
const LEGACY_THEME_STORAGE_KEY = 'spendsense_theme';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  readonly theme = signal<ThemePreference>(this.loadInitialPreference());
  readonly activeTheme = signal<ActiveTheme>(this.computeActiveTheme(this.loadInitialPreference()));

  private mediaQuery = typeof window !== 'undefined'
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null;

  constructor() {
    const initialPref = this.theme();
    const resolved = this.computeActiveTheme(initialPref);
    this.activeTheme.set(resolved);
    this.applyToDocument(resolved);

    // Listen to OS theme changes if on system preference
    if (this.mediaQuery) {
      this.mediaQuery.addEventListener('change', () => {
        if (this.theme() === 'system') {
          const res = this.computeActiveTheme('system');
          this.activeTheme.set(res);
          this.applyToDocument(res);
        }
      });
    }
  }

  setTheme(pref: ThemePreference): void {
    this.theme.set(pref);
    const resolved = this.computeActiveTheme(pref);
    this.activeTheme.set(resolved);
    this.applyToDocument(resolved);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(THEME_STORAGE_KEY, pref);
      localStorage.removeItem(LEGACY_THEME_STORAGE_KEY);
    }
  }

  toggleTheme(): void {
    const next: ThemePreference = this.activeTheme() === 'dark' ? 'light' : 'dark';
    this.setTheme(next);
  }

  private loadInitialPreference(): ThemePreference {
    if (typeof localStorage === 'undefined') return 'system';
    const stored = localStorage.getItem(THEME_STORAGE_KEY) ?? localStorage.getItem(LEGACY_THEME_STORAGE_KEY);
    if (stored === 'light' || stored === 'dark' || stored === 'system') {
      return stored;
    }
    return 'system';
  }

  private computeActiveTheme(pref: ThemePreference): ActiveTheme {
    if (pref === 'light') return 'light';
    if (pref === 'dark') return 'dark';
    return (this.mediaQuery && this.mediaQuery.matches) ? 'dark' : 'light';
  }

  private applyToDocument(active: ActiveTheme): void {
    if (typeof document !== 'undefined') {
      document.documentElement.setAttribute('data-theme', active);
    }
  }
}
