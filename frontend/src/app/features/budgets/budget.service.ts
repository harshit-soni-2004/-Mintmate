import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, Subject, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Budget, BudgetRequest, BudgetSummary } from '../../core/api/api.types';

@Injectable({ providedIn: 'root' })
export class BudgetService {
  private readonly url = `${environment.apiUrl}/budgets`;
  private readonly mutationSubject = new Subject<void>();

  readonly onBudgetMutated$: Observable<void> = this.mutationSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  list(): Observable<ApiResponse<Budget[]>> {
    return this.http.get<ApiResponse<Budget[]>>(this.url);
  }

  getSummary(): Observable<ApiResponse<BudgetSummary>> {
    return this.http.get<ApiResponse<BudgetSummary>>(`${this.url}/summary`);
  }

  getById(id: number): Observable<ApiResponse<Budget>> {
    return this.http.get<ApiResponse<Budget>>(`${this.url}/${id}`);
  }

  create(payload: BudgetRequest): Observable<ApiResponse<Budget>> {
    return this.http.post<ApiResponse<Budget>>(this.url, payload).pipe(
      tap(() => this.mutationSubject.next())
    );
  }

  update(id: number, payload: BudgetRequest): Observable<ApiResponse<Budget>> {
    return this.http.put<ApiResponse<Budget>>(`${this.url}/${id}`, payload).pipe(
      tap(() => this.mutationSubject.next())
    );
  }

  delete(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.url}/${id}`).pipe(
      tap(() => this.mutationSubject.next())
    );
  }
}
