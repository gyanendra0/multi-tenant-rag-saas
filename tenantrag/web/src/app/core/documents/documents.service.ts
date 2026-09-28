import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, switchMap, takeWhile, timer } from 'rxjs';

import { DocumentItem, DocumentPage, SpringPage, TERMINAL_STATUSES } from './document.models';

/** Thin client for `/api/documents` (tenant comes from the JWT, never the client). */
@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/documents';

  list(page: number, size: number): Observable<DocumentPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<SpringPage<DocumentItem>>(this.base, { params }).pipe(
      map((p) => ({
        items: p.content ?? [],
        total: p.page?.totalElements ?? p.totalElements ?? p.content?.length ?? 0,
      })),
    );
  }

  get(id: string): Observable<DocumentItem> {
    return this.http.get<DocumentItem>(`${this.base}/${id}`);
  }

  /** Multipart upload → 202 with `status: PENDING`. */
  upload(file: File): Observable<DocumentItem> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<DocumentItem>(this.base, form);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /**
   * Polls a document until READY/FAILED (emits every intermediate state,
   * including the terminal one, then completes). Gives up after `maxTries`.
   */
  pollUntilDone(id: string, intervalMs = 2000, maxTries = 90): Observable<DocumentItem> {
    return timer(intervalMs, intervalMs).pipe(
      takeWhile((i) => i < maxTries),
      switchMap(() => this.get(id)),
      takeWhile((doc) => !TERMINAL_STATUSES.has(doc.status), true),
    );
  }
}
