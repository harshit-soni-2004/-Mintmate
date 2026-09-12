import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, Subject, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  PageMeta,
  PaymentMethod,
  Transaction,
  TransactionCategory,
  TransactionRequest,
  TransactionType,
} from '../../core/api/api.types';

export interface TransactionFilterParams {
  page: number;
  size: number;
  search?: string;
  category?: TransactionCategory | '';
  type?: TransactionType | '';
  paymentMethod?: PaymentMethod | '';
  fromDate?: string;
  toDate?: string;
  minAmount?: number | null;
  maxAmount?: number | null;
  sort?: string;
}

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly url = `${environment.apiUrl}/transactions`;
  private readonly mutationSubject = new Subject<void>();

  readonly onTransactionMutated$: Observable<void> = this.mutationSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  list(filters: TransactionFilterParams): Observable<ApiResponse<Transaction[], PageMeta>> {
    let params = new HttpParams().set('page', filters.page).set('size', filters.size);
    if (filters.search) params = params.set('search', filters.search);
    if (filters.category) params = params.set('category', filters.category);
    if (filters.type) params = params.set('type', filters.type);
    if (filters.paymentMethod) params = params.set('paymentMethod', filters.paymentMethod);
    if (filters.fromDate) params = params.set('fromDate', filters.fromDate);
    if (filters.toDate) params = params.set('toDate', filters.toDate);
    if (filters.minAmount != null && filters.minAmount > 0) {
      params = params.set('minAmount', filters.minAmount.toString());
    }
    if (filters.maxAmount != null && filters.maxAmount > 0) {
      params = params.set('maxAmount', filters.maxAmount.toString());
    }
    if (filters.sort) params = params.set('sort', filters.sort);

    return this.http.get<ApiResponse<Transaction[], PageMeta>>(this.url, { params });
  }

  create(payload: TransactionRequest): Observable<ApiResponse<Transaction>> {
    return this.http.post<ApiResponse<Transaction>>(this.url, payload).pipe(
      tap(() => this.mutationSubject.next())
    );
  }

  update(id: string, payload: TransactionRequest): Observable<ApiResponse<Transaction>> {
    return this.http.put<ApiResponse<Transaction>>(`${this.url}/${id}`, payload).pipe(
      tap(() => this.mutationSubject.next())
    );
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`).pipe(
      tap(() => this.mutationSubject.next())
    );
  }

  notifyMutated(): void {
    this.mutationSubject.next();
  }
}
