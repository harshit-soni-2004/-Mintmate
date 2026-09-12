import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, Subject, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Goal, GoalContributionRequest, GoalRequest } from '../../core/api/api.types';

@Injectable({ providedIn: 'root' })
export class GoalService {
  private readonly url = `${environment.apiUrl}/goals`;
  private readonly mutationSubject = new Subject<void>();

  readonly onGoalMutated$: Observable<void> = this.mutationSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  list(): Observable<ApiResponse<Goal[]>> {
    return this.http.get<ApiResponse<Goal[]>>(this.url);
  }

  getById(id: number): Observable<ApiResponse<Goal>> {
    return this.http.get<ApiResponse<Goal>>(`${this.url}/${id}`);
  }

  create(payload: GoalRequest): Observable<ApiResponse<Goal>> {
    return this.http.post<ApiResponse<Goal>>(this.url, payload).pipe(
      tap(() => this.mutationSubject.next())
    );
  }

  update(id: number, payload: GoalRequest): Observable<ApiResponse<Goal>> {
    return this.http.put<ApiResponse<Goal>>(`${this.url}/${id}`, payload).pipe(
      tap(() => this.mutationSubject.next())
    );
  }

  delete(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.url}/${id}`).pipe(
      tap(() => this.mutationSubject.next())
    );
  }

  addContribution(id: number, payload: GoalContributionRequest): Observable<ApiResponse<Goal>> {
    return this.http.patch<ApiResponse<Goal>>(`${this.url}/${id}/contribution`, payload).pipe(
      tap(() => this.mutationSubject.next())
    );
  }
}
