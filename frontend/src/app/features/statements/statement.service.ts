import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  FinancialDocument,
  StatementSettlement,
  StatementUploadResponse,
} from '../../core/api/api.types';

export interface StatementUploadOptions {
  file: File;
  accountType: 'SAVINGS' | 'CURRENT' | 'CREDIT_CARD';
  institutionName?: string;
  accountNumberLast4?: string;
  statementStartDate?: string;
  statementEndDate?: string;
}

@Injectable({ providedIn: 'root' })
export class StatementService {
  private readonly baseUrl = `${environment.apiUrl}/statements`;

  constructor(private readonly http: HttpClient) {}

  uploadAndIngest(options: StatementUploadOptions): Observable<ApiResponse<StatementUploadResponse>> {
    const formData = new FormData();
    formData.append('file', options.file, options.file.name);
    formData.append('accountType', options.accountType);

    if (options.institutionName?.trim()) {
      formData.append('institutionName', options.institutionName.trim());
    }
    if (options.accountNumberLast4?.trim()) {
      formData.append('accountNumberLast4', options.accountNumberLast4.trim());
    }
    if (options.statementStartDate) {
      formData.append('statementStartDate', options.statementStartDate);
    }
    if (options.statementEndDate) {
      formData.append('statementEndDate', options.statementEndDate);
    }

    return this.http.post<ApiResponse<StatementUploadResponse>>(`${this.baseUrl}/upload`, formData);
  }

  list(): Observable<ApiResponse<FinancialDocument[]>> {
    return this.http.get<ApiResponse<FinancialDocument[]>>(this.baseUrl);
  }

  get(id: string): Observable<ApiResponse<FinancialDocument>> {
    return this.http.get<ApiResponse<FinancialDocument>>(`${this.baseUrl}/${id}`);
  }

  reingest(id: string): Observable<ApiResponse<StatementUploadResponse>> {
    return this.http.post<ApiResponse<StatementUploadResponse>>(`${this.baseUrl}/${id}/ingest`, {});
  }

  listSettlements(id: string): Observable<ApiResponse<StatementSettlement[]>> {
    return this.http.get<ApiResponse<StatementSettlement[]>>(`${this.baseUrl}/${id}/settlements`);
  }

  delete(id: string): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/${id}`);
  }
}
