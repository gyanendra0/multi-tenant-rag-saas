/** Mirrors backend `DocumentResponse`. */
export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';

export interface DocumentItem {
  id: string;
  uploadedBy: string | null;
  title: string;
  filename: string;
  mimeType: string | null;
  sizeBytes: number | null;
  status: DocumentStatus;
  errorMessage: string | null;
  chunkCount: number | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Spring `Page<T>` JSON. Spring Boot 3.3+ may serialize either the classic
 * shape (`totalElements` at top level) or the stable DTO shape (`page: {...}`),
 * so both are optional here and normalized in the service.
 */
export interface SpringPage<T> {
  content: T[];
  totalElements?: number;
  page?: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface DocumentPage {
  items: DocumentItem[];
  total: number;
}

export const TERMINAL_STATUSES: ReadonlySet<DocumentStatus> = new Set(['READY', 'FAILED']);
