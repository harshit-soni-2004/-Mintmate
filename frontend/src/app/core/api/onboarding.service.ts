import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, OnboardingRequest, OnboardingResponse } from './api.types';

@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly baseUrl = `${environment.apiUrl}/onboarding`;

  constructor(private readonly http: HttpClient) {}

  complete(request: OnboardingRequest): Observable<ApiResponse<OnboardingResponse>> {
    return this.http.post<ApiResponse<OnboardingResponse>>(`${this.baseUrl}/complete`, request);
  }

  getProfile(): Observable<ApiResponse<OnboardingResponse>> {
    return this.http.get<ApiResponse<OnboardingResponse>>(`${this.baseUrl}/profile`);
  }

  checkMissingSalaryAlert(): Observable<ApiResponse<{ hasAlert: string; message?: string }>> {
    return this.http.get<ApiResponse<{ hasAlert: string; message?: string }>>(
      `${this.baseUrl}/missing-salary-alert`
    );
  }
}
