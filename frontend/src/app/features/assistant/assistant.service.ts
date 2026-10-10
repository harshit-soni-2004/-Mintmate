import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  AssistantChatRequest,
  AssistantChatResponse,
  ChatMessage,
  PendingActionResult,
  ProactiveInsightDto,
} from '../../core/api/api.types';

@Injectable({ providedIn: 'root' })
export class AssistantService {
  private readonly baseUrl = `${environment.apiUrl}/assistant`;

  constructor(private readonly http: HttpClient) {}

  chat(message: string, conversationHistory: ChatMessage[] = []): Observable<ApiResponse<AssistantChatResponse>> {
    const payload: AssistantChatRequest = {
      message,
      conversationHistory,
    };
    return this.http.post<ApiResponse<AssistantChatResponse>>(`${this.baseUrl}/chat`, payload);
  }

  confirmAction(actionId: string): Observable<ApiResponse<PendingActionResult>> {
    return this.http.post<ApiResponse<PendingActionResult>>(`${this.baseUrl}/actions/${actionId}/confirm`, {});
  }

  cancelAction(actionId: string): Observable<ApiResponse<{ message: string }>> {
    return this.http.post<ApiResponse<{ message: string }>>(`${this.baseUrl}/actions/${actionId}/cancel`, {});
  }

  getProactiveInsights(): Observable<ApiResponse<ProactiveInsightDto[]>> {
    return this.http.get<ApiResponse<ProactiveInsightDto[]>>(`${this.baseUrl}/proactive-insights`);
  }

  dismissInsight(insightId: string): Observable<ApiResponse<{ message: string }>> {
    return this.http.post<ApiResponse<{ message: string }>>(`${this.baseUrl}/proactive-insights/${insightId}/dismiss`, {});
  }
}
