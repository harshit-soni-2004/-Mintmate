import { TestBed } from '@angular/core/testing';
import { ThemeService, ThemePreference } from './theme.service';

describe('ThemeService', () => {
  let service: ThemeService;

  beforeEach(() => {
    localStorage.removeItem('mintmate_theme');
    localStorage.removeItem('spendsense_theme');
    document.documentElement.removeAttribute('data-theme');
    TestBed.configureTestingModule({});
    service = TestBed.inject(ThemeService);
  });

  afterEach(() => {
    localStorage.removeItem('mintmate_theme');
    localStorage.removeItem('spendsense_theme');
    document.documentElement.removeAttribute('data-theme');
  });

  it('should initialize with system preference by default', () => {
    expect(service.theme()).toBe('system');
    expect(['light', 'dark']).toContain(service.activeTheme());
  });

  it('should switch to dark theme and persist in localStorage', () => {
    service.setTheme('dark');
    expect(service.theme()).toBe('dark');
    expect(service.activeTheme()).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('mintmate_theme')).toBe('dark');
  });

  it('should switch to light theme and persist in localStorage', () => {
    service.setTheme('light');
    expect(service.theme()).toBe('light');
    expect(service.activeTheme()).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(localStorage.getItem('mintmate_theme')).toBe('light');
  });

  it('should toggle between light and dark', () => {
    service.setTheme('light');
    service.toggleTheme();
    expect(service.activeTheme()).toBe('dark');
    service.toggleTheme();
    expect(service.activeTheme()).toBe('light');
  });
});
