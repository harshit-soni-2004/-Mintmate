import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  AggregationPeriod,
  ApiResponse,
  CategoryBreakdown,
  FinancialInsight,
  InsightsOverview,
  PeriodComparison,
  SpendingAnomaly,
  SpendingSummary,
  SpendingTrend,
} from '../../core/api/api.types';

export interface InsightQuery {
  month?: string;
  fromDate?: string;
  toDate?: string;
  trendPeriod?: AggregationPeriod;
}

@Injectable({
  providedIn: 'root',
})
export class InsightsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/insights`;

  getOverview(query?: InsightQuery): Observable<InsightsOverview> {
    const params = this.buildParams(query);
    return this.http
      .get<ApiResponse<InsightsOverview>>(this.baseUrl, { params })
      .pipe(map((res) => res.data!));
  }

  getSummary(query?: InsightQuery): Observable<SpendingSummary> {
    const params = this.buildParams(query);
    return this.http
      .get<ApiResponse<SpendingSummary>>(`${this.baseUrl}/summary`, { params })
      .pipe(map((res) => res.data!));
  }

  getComparison(query?: InsightQuery): Observable<PeriodComparison> {
    const params = this.buildParams(query);
    return this.http
      .get<ApiResponse<PeriodComparison>>(`${this.baseUrl}/comparison`, { params })
      .pipe(map((res) => res.data!));
  }

  getCategories(query?: InsightQuery): Observable<CategoryBreakdown> {
    const params = this.buildParams(query);
    return this.http
      .get<ApiResponse<CategoryBreakdown>>(`${this.baseUrl}/categories`, { params })
      .pipe(map((res) => res.data!));
  }

  getTrends(period?: AggregationPeriod, query?: InsightQuery): Observable<SpendingTrend> {
    let params = this.buildParams(query);
    if (period) {
      params = params.set('period', period);
    }
    return this.http
      .get<ApiResponse<SpendingTrend>>(`${this.baseUrl}/trends`, { params })
      .pipe(map((res) => res.data!));
  }

  getAnomalies(query?: InsightQuery): Observable<SpendingAnomaly[]> {
    const params = this.buildParams(query);
    return this.http
      .get<ApiResponse<SpendingAnomaly[]>>(`${this.baseUrl}/anomalies`, { params })
      .pipe(map((res) => res.data || []));
  }

  getInsights(query?: InsightQuery): Observable<FinancialInsight[]> {
    const params = this.buildParams(query);
    return this.http
      .get<ApiResponse<FinancialInsight[]>>(`${this.baseUrl}/items`, { params })
      .pipe(map((res) => res.data || []));
  }

  private buildParams(query?: InsightQuery): HttpParams {
    let params = new HttpParams();
    if (!query) return params;
    if (query.month) params = params.set('month', query.month);
    if (query.fromDate) params = params.set('fromDate', query.fromDate);
    if (query.toDate) params = params.set('toDate', query.toDate);
    if (query.trendPeriod) params = params.set('trendPeriod', query.trendPeriod);
    return params;
  }
}
