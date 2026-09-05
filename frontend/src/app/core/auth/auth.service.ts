import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, AuthResponse, User } from '../api/api.types';

const TOKEN_KEY = 'spendsense.token';
const USER_KEY = 'spendsense.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenState = signal<string | null>(this.readToken());
  private readonly userState = signal<User | null>(this.readUser());

  readonly token = this.tokenState.asReadonly();
  readonly currentUser = this.userState.asReadonly();
  readonly isAuthenticated = computed(() => !!this.tokenState());

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  register(payload: {
    email: string;
    password: string;
    displayName: string;
  }): Observable<ApiResponse<AuthResponse>> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${environment.apiUrl}/auth/register`, payload)
      .pipe(tap((response) => this.persist(response.data)));
  }

  login(payload: { email: string; password: string }): Observable<ApiResponse<AuthResponse>> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${environment.apiUrl}/auth/login`, payload)
      .pipe(tap((response) => this.persist(response.data)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.tokenState.set(null);
    this.userState.set(null);
    void this.router.navigate(['/login']);
  }

  private persist(data: AuthResponse | null): void {
    if (!data) {
      return;
    }
    localStorage.setItem(TOKEN_KEY, data.accessToken);
    localStorage.setItem(USER_KEY, JSON.stringify(data.user));
    this.tokenState.set(data.accessToken);
    this.userState.set(data.user);
  }

  private readToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  private readUser(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  }
}
