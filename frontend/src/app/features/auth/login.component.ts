import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiResponse } from '../../core/api/api.types';
import { environment } from '../../../environments/environment';
import { OnboardingHeroComponent } from './onboarding-hero.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, OnboardingHeroComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  mode: 'login' | 'forgot-request' | 'forgot-reset' = 'login';
  submitting = false;
  resending = false;
  errorMessage = '';
  successMessage = '';
  showPassword = false;
  showNewPassword = false;

  isEmailUnverified = false;
  unverifiedEmail = '';

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  readonly forgotRequestForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  readonly resetPasswordForm = this.fb.nonNullable.group(
    {
      email: ['', [Validators.required, Validators.email]],
      code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
      newPassword: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/),
        ],
      ],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  ngOnInit(): void {
    this.initGoogleAuth();
  }

  isFieldInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!ctrl && ctrl.invalid && (ctrl.touched || ctrl.dirty);
  }

  isForgotFieldInvalid(field: string): boolean {
    const ctrl = this.forgotRequestForm.get(field);
    return !!ctrl && ctrl.invalid && (ctrl.touched || ctrl.dirty);
  }

  isResetFieldInvalid(field: string): boolean {
    const ctrl = this.resetPasswordForm.get(field);
    return !!ctrl && ctrl.invalid && (ctrl.touched || ctrl.dirty);
  }

  hasResetPasswordMismatch(): boolean {
    const confirm = this.resetPasswordForm.get('confirmPassword');
    return (
      !!this.resetPasswordForm.hasError('passwordMismatch') &&
      !!confirm &&
      (confirm.touched || confirm.dirty)
    );
  }

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  toggleNewPassword(): void {
    this.showNewPassword = !this.showNewPassword;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.isEmailUnverified = false;

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        void this.router.navigate(['/app/dashboard']);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        if (err.error?.error?.code === 'EMAIL_NOT_VERIFIED') {
          this.isEmailUnverified = true;
          this.unverifiedEmail = this.form.controls.email.value;
          this.errorMessage = 'Please verify your email address before logging in.';
        } else {
          this.isEmailUnverified = false;
          this.errorMessage = messageFrom(err);
        }
      },
    });
  }

  goToVerifyOtp(): void {
    const email = this.unverifiedEmail || this.form.controls.email.value;
    void this.router.navigate(['/verify-otp'], {
      queryParams: { email },
    });
  }

  resendVerification(): void {
    const email = this.unverifiedEmail || this.form.controls.email.value;
    if (!email) return;
    this.resending = true;
    this.auth.sendOtp(email).subscribe({
      next: () => {
        this.resending = false;
        void this.router.navigate(['/verify-otp'], {
          queryParams: { email },
        });
      },
      error: () => {
        this.resending = false;
        void this.router.navigate(['/verify-otp'], {
          queryParams: { email },
        });
      },
    });
  }

  openForgotPassword(): void {
    this.mode = 'forgot-request';
    this.errorMessage = '';
    this.successMessage = '';
    const currentEmail = this.form.controls.email.value;
    if (currentEmail) {
      this.forgotRequestForm.patchValue({ email: currentEmail });
    }
  }

  backToLogin(): void {
    this.mode = 'login';
    this.errorMessage = '';
    this.successMessage = '';
  }

  submitForgotRequest(): void {
    if (this.forgotRequestForm.invalid) {
      this.forgotRequestForm.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.errorMessage = '';
    this.successMessage = '';
    const email = this.forgotRequestForm.controls.email.value.trim();

    this.auth.forgotPassword(email).subscribe({
      next: (res) => {
        this.submitting = false;
        this.resetPasswordForm.patchValue({ email });
        this.mode = 'forgot-reset';
        this.successMessage = res.data?.message || 'If an account is associated with this email, a reset code has been sent.';
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        this.errorMessage = messageFrom(err);
      },
    });
  }

  submitPasswordReset(): void {
    if (this.resetPasswordForm.invalid) {
      this.resetPasswordForm.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.errorMessage = '';
    this.successMessage = '';
    const { confirmPassword, ...payload } = this.resetPasswordForm.getRawValue();

    this.auth.resetPassword(payload).subscribe({
      next: (res) => {
        this.submitting = false;
        this.mode = 'login';
        this.form.patchValue({ email: payload.email, password: '' });
        this.successMessage = res.data?.message || 'Password has been reset successfully! Please sign in.';
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        this.errorMessage = messageFrom(err);
      },
    });
  }

  initGoogleAuth(): void {
    if (typeof window === 'undefined') return;
    const clientId = environment.googleClientId;
    if (!clientId) return;

    const w = window as any;
    const renderGsi = () => {
      if (w.google?.accounts?.id) {
        w.google.accounts.id.initialize({
          client_id: clientId,
          callback: (response: { credential?: string }) => {
            if (response.credential) {
              this.handleGoogleCredential(response.credential);
            }
          },
        });
        const container = document.getElementById('google-btn-slot');
        if (container) {
          w.google.accounts.id.renderButton(container, {
            theme: 'outline',
            size: 'large',
            width: 380,
            text: 'continue_with',
          });
        }
      }
    };

    if (w.google?.accounts?.id) {
      renderGsi();
    } else {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.defer = true;
      script.onload = () => renderGsi();
      document.head.appendChild(script);
    }
  }

  triggerGooglePrompt(): void {
    const w = window as any;
    if (w.google?.accounts?.id) {
      w.google.accounts.id.prompt();
    } else {
      this.errorMessage = 'Google Sign-In is initializing. Please try again in a moment.';
    }
  }

  handleGoogleCredential(idToken: string): void {
    this.submitting = true;
    this.errorMessage = '';
    this.auth.loginWithGoogle(idToken).subscribe({
      next: () => {
        void this.router.navigate(['/app/dashboard']);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        this.errorMessage = messageFrom(err);
      },
    });
  }
}

function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  const password = control.get('newPassword')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;
  return password && confirmPassword && password !== confirmPassword
    ? { passwordMismatch: true }
    : null;
}

export function messageFrom(err: HttpErrorResponse): string {
  const body = err.error as ApiResponse<unknown> | undefined;
  return body?.error?.message ?? 'Invalid email or password. Please try again.';
}
