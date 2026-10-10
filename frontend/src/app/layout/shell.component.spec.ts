import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShellComponent } from './shell.component';
import { AuthService } from '../core/auth/auth.service';
import { ThemeService } from '../core/theme/theme.service';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { signal } from '@angular/core';

import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('ShellComponent', () => {
  let component: ShellComponent;
  let fixture: ComponentFixture<ShellComponent>;
  let themeService: ThemeService;

  beforeEach(async () => {
    const authServiceMock = {
      currentUser: signal({ id: 'u1', email: 'test@example.com', displayName: 'Test User' }),
      logout: jasmine.createSpy('logout')
    };

    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        ThemeService,
        { provide: AuthService, useValue: authServiceMock },
        { provide: ActivatedRoute, useValue: {} }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ShellComponent);
    component = fixture.componentInstance;
    themeService = TestBed.inject(ThemeService);
    fixture.detectChanges();
  });

  it('should create and render floating assistant and theme toggle', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-floating-assistant')).toBeTruthy();
    expect(compiled.querySelector('.theme-toggle-btn')).toBeTruthy();
  });

  it('should toggle theme when theme button is clicked', () => {
    themeService.setTheme('light');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const themeBtn = compiled.querySelector('.theme-toggle-btn') as HTMLButtonElement;
    themeBtn.click();
    expect(themeService.activeTheme()).toBe('dark');
  });

  it('should render mobile header with brand and profile button', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const mobileHeader = compiled.querySelector('.mobile-header');
    expect(mobileHeader).toBeTruthy();
    expect(compiled.querySelector('.mobile-brand-name')?.textContent).toContain('Mintmate');
    expect(compiled.querySelector('.mobile-profile-btn')).toBeTruthy();
  });

  it('should render 4 primary destinations in mobile bottom navigation', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const mobileNavItems = compiled.querySelectorAll('.mobile-nav-item');
    expect(mobileNavItems.length).toBe(4);

    const labels = Array.from(mobileNavItems).map((item) => item.textContent?.trim());
    expect(labels).toEqual(['Home', 'Activity', 'Insights', 'AI']);
  });

  it('should toggle profile drawer when mobile profile button is clicked', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const profileBtn = compiled.querySelector('.mobile-profile-btn') as HTMLButtonElement;

    expect(component.isProfileSheetOpen()).toBeFalse();
    expect(compiled.querySelector('.profile-drawer')).toBeFalsy();

    profileBtn.click();
    fixture.detectChanges();

    expect(component.isProfileSheetOpen()).toBeTrue();
    expect(compiled.querySelector('.profile-drawer')).toBeTruthy();
    expect(compiled.querySelector('.drawer-name')?.textContent).toContain('Test User');

    // Close drawer
    const closeBtn = compiled.querySelector('.btn-close-drawer') as HTMLButtonElement;
    closeBtn.click();
    fixture.detectChanges();

    expect(component.isProfileSheetOpen()).toBeFalse();
    expect(compiled.querySelector('.profile-drawer')).toBeFalsy();
  });
});
