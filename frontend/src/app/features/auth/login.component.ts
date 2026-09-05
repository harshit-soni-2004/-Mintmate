import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiResponse } from '../../core/api/api.types';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  submitting = false;
  errorMessage = '';

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

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
}

export function messageFrom(err: HttpErrorResponse): string {
  const body = err.error as ApiResponse<unknown> | undefined;
  return body?.error?.message ?? 'Something went wrong. Please try again.';
}
