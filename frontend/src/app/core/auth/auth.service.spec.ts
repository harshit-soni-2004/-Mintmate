import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('stores the session after login', () => {
    service.login({ email: 'ada@example.com', password: 'password1' }).subscribe();

    const req = http.expectOne(`${environment.apiUrl}/auth/login`);
    req.flush({
      data: {
        accessToken: 'token-1',
        tokenType: 'Bearer',
        user: {
          id: '11111111-1111-1111-1111-111111111111',
          email: 'ada@example.com',
          displayName: 'Ada',
          timezone: 'Asia/Kolkata',
          createdAt: '2026-01-01T00:00:00Z',
        },
      },
      error: null,
      meta: null,
    });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.currentUser()?.displayName).toBe('Ada');
    expect(localStorage.getItem('spendsense.token')).toBe('token-1');
  });
});
