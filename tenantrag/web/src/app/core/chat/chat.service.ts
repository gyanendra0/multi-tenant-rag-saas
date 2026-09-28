import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ChatMessage, ChatRequest, ChatResponse, ConversationSummary } from './chat.models';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  ask(req: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>('/api/chat', req);
  }

  conversations(limit = 50, offset = 0): Observable<ConversationSummary[]> {
    const params = new HttpParams().set('limit', limit).set('offset', offset);
    return this.http.get<ConversationSummary[]>('/api/conversations', { params });
  }

  messages(conversationId: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`/api/conversations/${conversationId}/messages`);
  }

  deleteConversation(conversationId: string): Observable<void> {
    return this.http.delete<void>(`/api/conversations/${conversationId}`);
  }
}
