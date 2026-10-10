import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SettingsComponent } from './settings.component';
import { ThemeService } from '../../core/theme/theme.service';
import { AuthService } from '../../core/auth/auth.service';
import { signal } from '@angular/core';

describe('SettingsComponent', () => {
  let component: SettingsComponent;
  let fixture: ComponentFixture<SettingsComponent>;
  let themeService: ThemeService;

  beforeEach(async () => {
    const authServiceMock = {
      currentUser: signal({ id: 'u1', email: 'test@example.com', displayName: 'Test User' })
    };

    await TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        ThemeService,
        { provide: AuthService, useValue: authServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    themeService = TestBed.inject(ThemeService);
    fixture.detectChanges();
  });

  it('should create and render theme selection cards', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.theme-card').length).toBe(3);
  });

  it('should select dark theme when clicked', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const cards = compiled.querySelectorAll('.theme-card') as NodeListOf<HTMLButtonElement>;
    // 3rd card is dark
    cards[2].click();
    expect(themeService.theme()).toBe('dark');
  });

  it('should select light theme when clicked', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const cards = compiled.querySelectorAll('.theme-card') as NodeListOf<HTMLButtonElement>;
    // 2nd card is light
    cards[1].click();
    expect(themeService.theme()).toBe('light');
  });
});
