import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', [
      'login',
      'loginWithGoogle',
      'sendOtp',
      'forgotPassword',
      'resetPassword',
    ]);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
    fixture.detectChanges();
  });

  it('validates email and password requirements', () => {
    expect(component.form.valid).toBeFalse();

    component.form.controls.email.setValue('invalid-email');
    component.form.controls.password.setValue('short');
    expect(component.form.valid).toBeFalse();

    component.form.controls.email.setValue('user@example.com');
    component.form.controls.password.setValue('ValidPass123');
    expect(component.form.valid).toBeTrue();
  });

  it('marks email field invalid with aria-invalid and has-error class on blur/touch', () => {
    const emailInput: HTMLInputElement = fixture.nativeElement.querySelector('#email');
    component.form.controls.email.setValue('not-an-email');
    component.form.controls.email.markAsTouched();
    fixture.detectChanges();

    expect(component.isFieldInvalid('email')).toBeTrue();
    const fieldGroup = emailInput.closest('.field-group');
    expect(fieldGroup?.classList.contains('has-error')).toBeTrue();
    expect(emailInput.getAttribute('aria-invalid')).toBe('true');
  });

  it('toggles password visibility', () => {
    expect(component.showPassword).toBeFalse();
    component.togglePassword();
    expect(component.showPassword).toBeTrue();
  });

  it('submits login payload and routes to dashboard', () => {
    authServiceSpy.login.and.returnValue(
      of({
        data: {
          accessToken: 'jwt-login-xyz',
          tokenType: 'Bearer',
          user: {
            id: '123',
            email: 'user@example.com',
            displayName: 'User',
            timezone: 'UTC',
            createdAt: '2026-01-01T00:00:00Z',
          },
        },
        error: null,
        meta: null,
      }),
    );

    component.form.setValue({
      email: 'user@example.com',
      password: 'ValidPass123',
    });

    component.submit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'ValidPass123',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/app/dashboard']);
  });

  it('handles unverified email error by displaying notice and offering verification navigation', () => {
    const unverifiedError = new HttpErrorResponse({
      status: 403,
      error: {
        error: {
          code: 'EMAIL_NOT_VERIFIED',
          message: 'Please verify your email address before logging in.',
        },
      },
    });
    authServiceSpy.login.and.returnValue(throwError(() => unverifiedError));

    component.form.setValue({
      email: 'unverified@example.com',
      password: 'CorrectPassword123',
    });

    component.submit();
    fixture.detectChanges();

    expect(component.isEmailUnverified).toBeTrue();
    expect(component.unverifiedEmail).toBe('unverified@example.com');

    const notice = fixture.nativeElement.querySelector('#unverified-email-notice');
    expect(notice).toBeTruthy();

    component.goToVerifyOtp();
    expect(router.navigate).toHaveBeenCalledWith(['/verify-otp'], {
      queryParams: { email: 'unverified@example.com' },
    });
  });

  it('handles wrong password with generic error message without exposing verification status', () => {
    const invalidCredsError = new HttpErrorResponse({
      status: 401,
      error: {
        error: {
          code: 'INVALID_CREDENTIALS',
          message: 'Email or password is incorrect',
        },
      },
    });
    authServiceSpy.login.and.returnValue(throwError(() => invalidCredsError));

    component.form.setValue({
      email: 'user@example.com',
      password: 'WrongPassword123',
    });

    component.submit();
    fixture.detectChanges();

    expect(component.isEmailUnverified).toBeFalse();
    expect(component.errorMessage).toBe('Email or password is incorrect');
  });

  it('renders Google sign-in button and triggers loginWithGoogle on credential', () => {
    const googleBtn = fixture.nativeElement.querySelector('#btn-google-login');
    expect(googleBtn).toBeTruthy();

    authServiceSpy.loginWithGoogle.and.returnValue(
      of({
        data: {
          accessToken: 'jwt-google-xyz',
          tokenType: 'Bearer',
          user: {
            id: 'google-user-1',
            email: 'google@example.com',
            displayName: 'Google User',
            timezone: 'Asia/Kolkata',
            createdAt: '2026-10-17T00:00:00Z',
          },
        },
        error: null,
        meta: null,
      }),
    );

    component.handleGoogleCredential('test-id-token-xyz');

    expect(authServiceSpy.loginWithGoogle).toHaveBeenCalledWith('test-id-token-xyz');
    expect(router.navigate).toHaveBeenCalledWith(['/app/dashboard']);
  });

  it('supports full forgot password and reset password flow', () => {
    component.form.controls.email.setValue('resetme@example.com');
    component.openForgotPassword();
    expect(component.mode).toBe('forgot-request');
    expect(component.forgotRequestForm.controls.email.value).toBe('resetme@example.com');

    authServiceSpy.forgotPassword.and.returnValue(
      of({
        data: { success: true, message: 'Reset code sent', resendCooldownSeconds: 60 },
        error: null,
        meta: null,
      }),
    );

    component.submitForgotRequest();
    expect(authServiceSpy.forgotPassword).toHaveBeenCalledWith('resetme@example.com');
    expect(component.mode).toBe('forgot-reset');
    expect(component.resetPasswordForm.controls.email.value).toBe('resetme@example.com');

    authServiceSpy.resetPassword.and.returnValue(
      of({
        data: { success: true, message: 'Password has been reset successfully!', resendCooldownSeconds: 0 },
        error: null,
        meta: null,
      }),
    );

    component.resetPasswordForm.patchValue({
      code: '123456',
      newPassword: 'BrandNewPassword1',
      confirmPassword: 'BrandNewPassword1',
    });

    component.submitPasswordReset();
    expect(authServiceSpy.resetPassword).toHaveBeenCalledWith({
      email: 'resetme@example.com',
      code: '123456',
      newPassword: 'BrandNewPassword1',
    });
    expect(component.mode).toBe('login');
    expect(component.successMessage).toContain('Password has been reset successfully!');
  });
});
