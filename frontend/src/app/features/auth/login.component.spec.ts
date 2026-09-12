import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login']);

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
});
