import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { VerifyOtpComponent } from './verify-otp.component';

describe('VerifyOtpComponent', () => {
  let component: VerifyOtpComponent;
  let fixture: ComponentFixture<VerifyOtpComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['verifyOtp', 'sendOtp', 'isAuthenticated']);
    authServiceSpy.isAuthenticated.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [VerifyOtpComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => (key === 'email' ? 'test@example.com' : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture = TestBed.createComponent(VerifyOtpComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('initializes and pre-populates email from query parameter', () => {
    expect(component.form.controls.email.value).toBe('test@example.com');
  });

  it('validates 6-digit code requirement', () => {
    component.form.controls.code.setValue('123');
    expect(component.form.controls.code.invalid).toBeTrue();

    component.form.controls.code.setValue('123456');
    expect(component.form.controls.code.valid).toBeTrue();
  });

  it('submits valid OTP and triggers navigation on success', () => {
    authServiceSpy.verifyOtp.and.returnValue(
      of({
        data: {
          success: true,
          message: 'Email verified successfully.',
          resendCooldownSeconds: 0,
        },
        error: null,
        meta: null,
      }),
    );

    component.form.controls.code.setValue('654321');
    component.submit();

    expect(authServiceSpy.verifyOtp).toHaveBeenCalledWith('test@example.com', '654321');
    expect(component.successMessage).toBe('Email verified successfully.');
  });

  it('displays friendly message on expired OTP error', () => {
    const errorResponse = new HttpErrorResponse({
      status: 400,
      error: {
        error: {
          code: 'OTP_EXPIRED',
          message: 'Verification code has expired.',
        },
      },
    });
    authServiceSpy.verifyOtp.and.returnValue(throwError(() => errorResponse));

    component.form.controls.code.setValue('123456');
    component.submit();

    expect(component.errorMessage).toBe('Your verification code has expired. Please request a new one.');
  });

  it('triggers resendCode and initiates cooldown timer', () => {
    authServiceSpy.sendOtp.and.returnValue(
      of({
        data: {
          success: true,
          message: 'Code sent',
          resendCooldownSeconds: 45,
        },
        error: null,
        meta: null,
      }),
    );

    component.resendCode();

    expect(authServiceSpy.sendOtp).toHaveBeenCalledWith('test@example.com');
    expect(component.cooldownSeconds).toBe(45);
    expect(component.successMessage).toContain('new verification code has been dispatched');
  });
});
