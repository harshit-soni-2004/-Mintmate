import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';
import { messageFrom } from './login.component';
import { OnboardingHeroComponent } from './onboarding-hero.component';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, OnboardingHeroComponent],
  templateUrl: './register.component.html',
  styleUrl: './login.component.scss',
})
export class RegisterComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  submitting = false;
  errorMessage = '';
  showPassword = false;
  showConfirmPassword = false;

  ngOnInit(): void {
    this.initGoogleAuth();
  }

  readonly form = this.fb.nonNullable.group(
    {
      displayName: ['', [Validators.required, Validators.maxLength(80)]],
      email: ['', [Validators.required, Validators.email]],
      password: [
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

  isFieldInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!ctrl && ctrl.invalid && (ctrl.touched || ctrl.dirty);
  }

  hasPasswordMismatch(): boolean {
    const confirm = this.form.get('confirmPassword');
    return (
      !!this.form.hasError('passwordMismatch') &&
      !!confirm &&
      (confirm.touched || confirm.dirty)
    );
  }

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  toggleConfirmPassword(): void {
    this.showConfirmPassword = !this.showConfirmPassword;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.errorMessage = '';
    const { confirmPassword, ...payload } = this.form.getRawValue();
    this.auth.register(payload).subscribe({
      next: () => {
        void this.router.navigate(['/verify-otp'], {
          queryParams: { email: payload.email },
        });
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
            text: 'signup_with',
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
        void this.router.navigate(['/app/onboarding']);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        this.errorMessage = messageFrom(err);
      },
    });
  }
}

function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;
  return password && confirmPassword && password !== confirmPassword
    ? { passwordMismatch: true }
    : null;
}
