import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, Subject, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  CreateDebtRequest,
  DebtCandidateTransactionDto,
  DebtDto,
  DebtStatus,
  DebtSummaryDto,
  SettleDebtRequest,
} from './api.types';

@Injectable({ providedIn: 'root' })
export class DebtService {
  private readonly url = `${environment.apiUrl}/debts`;
  private readonly mutationSubject = new Subject<void>();

  readonly onDebtMutated$: Observable<void> = this.mutationSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  list(status?: DebtStatus): Observable<ApiResponse<DebtDto[]>> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<ApiResponse<DebtDto[]>>(this.url, { params });
  }

  getSummary(): Observable<ApiResponse<DebtSummaryDto>> {
    return this.http.get<ApiResponse<DebtSummaryDto>>(`${this.url}/summary`);
  }

  getById(id: string): Observable<ApiResponse<DebtDto>> {
    return this.http.get<ApiResponse<DebtDto>>(`${this.url}/${id}`);
  }

  getCandidates(id: string): Observable<ApiResponse<DebtCandidateTransactionDto[]>> {
    return this.http.get<ApiResponse<DebtCandidateTransactionDto[]>>(`${this.url}/${id}/candidates`);
  }

  create(payload: CreateDebtRequest): Observable<ApiResponse<DebtDto>> {
    return this.http.post<ApiResponse<DebtDto>>(this.url, payload).pipe(
      tap(() => this.mutationSubject.next())
    );
  }

  settle(id: string, payload?: SettleDebtRequest): Observable<ApiResponse<DebtDto>> {
    return this.http.post<ApiResponse<DebtDto>>(`${this.url}/${id}/settle`, payload ?? {}).pipe(
      tap(() => this.mutationSubject.next())
    );
  }

  linkTransaction(id: string, payload: SettleDebtRequest): Observable<ApiResponse<DebtDto>> {
    return this.http.post<ApiResponse<DebtDto>>(`${this.url}/${id}/link-transaction`, payload).pipe(
      tap(() => this.mutationSubject.next())
    );
  }

  dismissCandidate(debtId: string, transactionId: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.url}/${debtId}/candidates/${transactionId}/dismiss`, {});
  }

  cancel(id: string): Observable<ApiResponse<DebtDto>> {
    return this.http.post<ApiResponse<DebtDto>>(`${this.url}/${id}/cancel`, {}).pipe(
      tap(() => this.mutationSubject.next())
    );
  }
}
