import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let fixture: ComponentFixture<RegisterComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['register', 'loginWithGoogle']);

    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
    fixture.detectChanges();
  });

  it('initializes with invalid form', () => {
    expect(component.form.valid).toBeFalse();
  });

  it('validates password and confirmPassword match', () => {
    component.form.controls.displayName.setValue('Alex Morgan');
    component.form.controls.email.setValue('alex@example.com');
    component.form.controls.password.setValue('Password123');
    component.form.controls.confirmPassword.setValue('Password999');
    component.form.controls.confirmPassword.markAsTouched();

    expect(component.form.hasError('passwordMismatch')).toBeTrue();
    expect(component.hasPasswordMismatch()).toBeTrue();

    component.form.controls.confirmPassword.setValue('Password123');
    expect(component.form.hasError('passwordMismatch')).toBeFalse();
    expect(component.hasPasswordMismatch()).toBeFalse();
    expect(component.form.valid).toBeTrue();
  });

  it('toggles password and confirm password visibility', () => {
    expect(component.showPassword).toBeFalse();
    component.togglePassword();
    expect(component.showPassword).toBeTrue();

    expect(component.showConfirmPassword).toBeFalse();
    component.toggleConfirmPassword();
    expect(component.showConfirmPassword).toBeTrue();
  });

  it('submits valid registration payload excluding confirmPassword and navigates', () => {
    authServiceSpy.register.and.returnValue(
      of({
        data: {
          accessToken: 'jwt-xyz',
          tokenType: 'Bearer',
          user: {
            id: '123',
            email: 'alex@example.com',
            displayName: 'Alex Morgan',
            timezone: 'UTC',
            createdAt: '2026-01-01T00:00:00Z',
          },
        },
        error: null,
        meta: null,
      }),
    );

    component.form.setValue({
      displayName: 'Alex Morgan',
      email: 'alex@example.com',
      password: 'Password123',
      confirmPassword: 'Password123',
    });

    component.submit();

    expect(authServiceSpy.register).toHaveBeenCalledWith({
      displayName: 'Alex Morgan',
      email: 'alex@example.com',
      password: 'Password123',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/app/onboarding']);
  });

  it('renders Google registration button and triggers loginWithGoogle on credential', () => {
    const googleBtn = fixture.nativeElement.querySelector('#btn-google-register');
    expect(googleBtn).toBeTruthy();

    authServiceSpy.loginWithGoogle.and.returnValue(
      of({
        data: {
          accessToken: 'jwt-google-reg',
          tokenType: 'Bearer',
          user: {
            id: 'google-reg-1',
            email: 'googlereg@example.com',
            displayName: 'Google Reg',
            timezone: 'Asia/Kolkata',
            createdAt: '2026-10-17T00:00:00Z',
          },
        },
        error: null,
        meta: null,
      }),
    );

    component.handleGoogleCredential('google-id-token-abc');

    expect(authServiceSpy.loginWithGoogle).toHaveBeenCalledWith('google-id-token-abc');
    expect(router.navigate).toHaveBeenCalledWith(['/app/onboarding']);
  });
});
