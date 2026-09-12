import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, DashboardData } from '../../core/api/api.types';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly url = `${environment.apiUrl}/dashboard`;

  constructor(private readonly http: HttpClient) {}

  getDashboard(filters?: {
    month?: string;
    fromDate?: string;
    toDate?: string;
  }): Observable<ApiResponse<DashboardData>> {
    let params = new HttpParams();
    if (filters?.month) params = params.set('month', filters.month);
    if (filters?.fromDate) params = params.set('fromDate', filters.fromDate);
    if (filters?.toDate) params = params.set('toDate', filters.toDate);
    return this.http.get<ApiResponse<DashboardData>>(this.url, { params });
  }
}
