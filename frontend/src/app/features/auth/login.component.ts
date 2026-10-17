import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
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

  submitting = false;
  errorMessage = '';
  showPassword = false;

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  ngOnInit(): void {
    this.initGoogleAuth();
  }

  isFieldInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!ctrl && ctrl.invalid && (ctrl.touched || ctrl.dirty);
  }

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.errorMessage = '';
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        void this.router.navigate(['/app/dashboard']);
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

export function messageFrom(err: HttpErrorResponse): string {
  const body = err.error as ApiResponse<unknown> | undefined;
  return body?.error?.message ?? 'Invalid email or password. Please try again.';
}
