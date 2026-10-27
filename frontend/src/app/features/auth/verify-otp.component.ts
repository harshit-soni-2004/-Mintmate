import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { OnboardingHeroComponent } from './onboarding-hero.component';

@Component({
  selector: 'app-verify-otp',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, OnboardingHeroComponent],
  templateUrl: './verify-otp.component.html',
  styleUrl: './login.component.scss',
})
export class VerifyOtpComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  submitting = false;
  resending = false;
  errorMessage = '';
  successMessage = '';
  cooldownSeconds = 0;
  private timerSub?: Subscription;

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  ngOnInit(): void {
    const emailParam = this.route.snapshot.queryParamMap.get('email');
    if (emailParam) {
      this.form.patchValue({ email: emailParam.trim() });
    }
  }

  ngOnDestroy(): void {
    this.timerSub?.unsubscribe();
  }

  isFieldInvalid(field: 'email' | 'code'): boolean {
    const ctrl = this.form.get(field);
    return !!ctrl && ctrl.invalid && (ctrl.dirty || ctrl.touched);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    const { email, code } = this.form.getRawValue();

    this.auth.verifyOtp(email.trim(), code.trim()).subscribe({
      next: (res) => {
        this.submitting = false;
        this.successMessage = res.data?.message || 'Email verified successfully!';
        setTimeout(() => {
          if (this.auth.isAuthenticated()) {
            void this.router.navigate(['/app/dashboard']);
          } else {
            void this.router.navigate(['/login']);
          }
        }, 1500);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        this.errorMessage = this.friendlyErrorMessage(err);
      },
    });
  }

  resendCode(): void {
    const email = this.form.controls.email.value.trim();
    if (!email || this.form.controls.email.invalid) {
      this.form.controls.email.markAsTouched();
      this.errorMessage = 'Please enter a valid email address to send a code.';
      return;
    }

    if (this.cooldownSeconds > 0 || this.resending) {
      return;
    }

    this.resending = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.auth.sendOtp(email).subscribe({
      next: (res) => {
        this.resending = false;
        this.successMessage = 'A new verification code has been dispatched to your email.';
        const cooldown = res.data?.resendCooldownSeconds || 60;
        this.startCooldown(cooldown);
      },
      error: (err: HttpErrorResponse) => {
        this.resending = false;
        this.errorMessage = this.friendlyErrorMessage(err);
      },
    });
  }

  startCooldown(seconds: number): void {
    this.cooldownSeconds = seconds;
    this.timerSub?.unsubscribe();
    this.timerSub = interval(1000).subscribe(() => {
      this.cooldownSeconds--;
      if (this.cooldownSeconds <= 0) {
        this.timerSub?.unsubscribe();
      }
    });
  }

  private friendlyErrorMessage(err: HttpErrorResponse): string {
    const code = err.error?.error?.code;
    const msg = err.error?.error?.message;

    if (code === 'OTP_EXPIRED') {
      return 'Your verification code has expired. Please request a new one.';
    }
    if (code === 'OTP_MAX_ATTEMPTS') {
      return 'Maximum verification attempts reached. Please request a new code.';
    }
    if (code === 'OTP_COOLDOWN') {
      return msg || 'Please wait a moment before requesting another code.';
    }
    if (code === 'RATE_LIMIT_EXCEEDED') {
      return 'Too many attempts. Please try again later.';
    }
    if (code === 'INVALID_OTP') {
      return 'The verification code entered is incorrect. Please check and try again.';
    }
    if (msg) {
      return msg;
    }
    return 'Unable to verify code. Please try again or request a new code.';
  }
}
