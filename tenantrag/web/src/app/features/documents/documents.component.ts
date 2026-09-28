import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter, switchMap } from 'rxjs';

import { DocumentItem, TERMINAL_STATUSES } from '../../core/documents/document.models';
import { DocumentsService } from '../../core/documents/documents.service';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';

/** Must match `spring.servlet.multipart.max-file-size` (25 MB). */
const MAX_UPLOAD_BYTES = 25 * 1024 * 1024;
const ACCEPT = '.pdf,.docx,.txt,.md,.html,.htm,.csv';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [
    DatePipe, DecimalPipe, MatTableModule, MatPaginatorModule, MatButtonModule,
    MatIconModule, MatProgressBarModule, MatTooltipModule,
  ],
  template: `
    <div class="page">
      <div class="header">
        <div>
          <h1>Documents</h1>
          <p class="muted sub">Files your team can ask questions about.</p>
        </div>
        <span class="spacer"></span>
        <input #fileInput type="file" hidden [accept]="accept" (change)="onFileChosen(fileInput)" />
        <button mat-flat-button color="primary" [disabled]="uploading()" (click)="fileInput.click()">
          <mat-icon>upload_file</mat-icon> Upload
        </button>
      </div>

      @if (loading() || uploading()) { <mat-progress-bar mode="indeterminate" /> }

      @if (docs().length === 0 && !loading()) {
        <div class="empty">
          <mat-icon>description</mat-icon>
          <h3>No documents yet</h3>
          <p class="muted">Upload a PDF, DOCX, TXT, MD, HTML or CSV file to get started.</p>
        </div>
      } @else {
        <div class="card">
          <table mat-table [dataSource]="docs()">
            <ng-container matColumnDef="title">
              <th mat-header-cell *matHeaderCellDef>Title</th>
              <td mat-cell *matCellDef="let d"><span class="title">{{ d.title }}</span></td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let d">
                <span class="badge" [class]="'badge ' + d.status.toLowerCase()"
                      [matTooltip]="d.errorMessage ?? ''">{{ d.status }}</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="chunks">
              <th mat-header-cell *matHeaderCellDef>Chunks</th>
              <td mat-cell *matCellDef="let d">{{ d.chunkCount ?? '—' }}</td>
            </ng-container>

            <ng-container matColumnDef="size">
              <th mat-header-cell *matHeaderCellDef>Size</th>
              <td mat-cell *matCellDef="let d">{{ (d.sizeBytes ?? 0) / 1024 | number: '1.0-1' }} KB</td>
            </ng-container>

            <ng-container matColumnDef="created">
              <th mat-header-cell *matHeaderCellDef>Uploaded</th>
              <td mat-cell *matCellDef="let d">{{ d.createdAt | date: 'medium' }}</td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let d">
                <button mat-icon-button aria-label="Delete" (click)="confirmDelete(d)">
                  <mat-icon>delete_outline</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>

          <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
                         [pageSizeOptions]="[10, 20, 50]" (page)="onPage($event)" />
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; height: 100%; overflow-y: auto; background: var(--bg); }

    .page { max-width: 960px; margin: 0 auto; padding: 32px 24px 48px; }

    .header { display: flex; align-items: flex-start; margin-bottom: 20px; }
    .sub { margin: 2px 0 0; font-size: 14px; }
    .spacer { flex: 1; }

    .card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      box-shadow: var(--shadow);
      overflow: hidden;
    }
    table { width: 100%; }
    .title { font-weight: 500; }

    .empty {
      text-align: center; padding: 72px 24px;
      background: var(--surface);
      border: 1px dashed var(--border);
      border-radius: var(--radius-lg);
    }
    .empty mat-icon { font-size: 40px; width: 40px; height: 40px; color: var(--text-muted); }
    .empty h3 { margin: 10px 0 4px; }

    .badge {
      display: inline-block; padding: 3px 10px; border-radius: 999px;
      font-size: 11px; font-weight: 600; letter-spacing: .03em;
    }
    .badge.pending    { background: var(--surface-sunken); color: var(--text-muted); }
    .badge.processing { background: var(--info-soft);      color: var(--info); }
    .badge.ready      { background: var(--ok-soft);        color: var(--ok); }
    .badge.failed     { background: var(--err-soft);       color: var(--err); cursor: help; }

    .muted { color: var(--text-muted); }
  `,
})
export class DocumentsComponent implements OnInit {
  private readonly api = inject(DocumentsService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly accept = ACCEPT;
  readonly columns = ['title', 'status', 'chunks', 'size', 'created', 'actions'];

  readonly docs = signal<DocumentItem[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly loading = signal(false);
  readonly uploading = signal(false);

  /** Ids already being polled — avoids duplicate pollers after a reload. */
  private readonly polling = new Set<string>();

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.list(this.pageIndex(), this.pageSize())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ items, total }) => {
          this.docs.set(items);
          this.total.set(total);
          this.loading.set(false);
          // Resume polling for anything still in flight (e.g. after a page reload).
          items.filter((d) => !TERMINAL_STATUSES.has(d.status)).forEach((d) => this.watch(d.id));
        },
        error: (e) => { this.loading.set(false); this.toast(this.message(e, 'Failed to load documents')); },
      });
  }

  onPage(e: PageEvent): void {
    this.pageIndex.set(e.pageIndex);
    this.pageSize.set(e.pageSize);
    this.load();
  }

  onFileChosen(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file
    if (!file) return;
    if (file.size > MAX_UPLOAD_BYTES) {
      this.toast('File is larger than 25 MB.');
      return;
    }

    this.uploading.set(true);
    this.api.upload(file).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (doc) => {
        this.uploading.set(false);
        this.toast(`Uploaded “${doc.title}” — processing…`);
        this.pageIndex.set(0); // newest first → show it on page 1
        this.load();
      },
      error: (e) => { this.uploading.set(false); this.toast(this.message(e, 'Upload failed')); },
    });
  }

  confirmDelete(doc: DocumentItem): void {
    const data: ConfirmDialogData = {
      title: 'Delete document?',
      message: `“${doc.title}” and all its chunks will be permanently removed.`,
      confirmLabel: 'Delete',
    };
    this.dialog.open(ConfirmDialogComponent, { data, width: '420px' })
      .afterClosed()
      .pipe(filter(Boolean), switchMap(() => this.api.delete(doc.id)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast('Document deleted');
          // Step back a page if we just emptied the last one.
          if (this.docs().length === 1 && this.pageIndex() > 0) this.pageIndex.update((p) => p - 1);
          this.load();
        },
        error: (e) => this.toast(this.message(e, 'Delete failed')),
      });
  }

  /** Poll one document and patch its row in place until READY/FAILED. */
  private watch(id: string): void {
    if (this.polling.has(id)) return;
    this.polling.add(id);
    this.api.pollUntilDone(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.docs.update((list) => list.map((d) => (d.id === id ? updated : d)));
        if (updated.status === 'FAILED') this.toast(`“${updated.title}” failed: ${updated.errorMessage ?? 'unknown error'}`);
      },
      error: () => this.polling.delete(id),       // e.g. 404 after delete
      complete: () => this.polling.delete(id),
    });
  }

  private message(e: unknown, fallback: string): string {
    if (e instanceof HttpErrorResponse) {
      if (e.status === 413) return 'File is too large.';
      return e.error?.message ?? fallback;
    }
    return fallback;
  }

  private toast(msg: string): void {
    this.snack.open(msg, 'OK', { duration: 4000 });
  }
}
