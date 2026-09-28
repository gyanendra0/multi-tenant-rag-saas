import { DecimalPipe, Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component, DestroyRef, ElementRef, OnInit, inject, signal, viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute } from '@angular/router';
import { filter, switchMap } from 'rxjs';

import { ChatMessage, ConversationSummary } from '../../core/chat/chat.models';
import { ChatService } from '../../core/chat/chat.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../documents/confirm-dialog.component';

/** Backend `ChatRequest.question` limit guard (keeps prompts sane). */
const MAX_QUESTION = 2000;

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    DecimalPipe, FormsModule, MatIconModule, MatProgressBarModule, MatTooltipModule,
  ],
  template: `
    <div class="layout">
      <!-- Flush against the left edge of the viewport. -->
      <aside class="sidebar">
        <button class="new" (click)="newChat()">
          <mat-icon>add</mat-icon><span>New chat</span>
        </button>

        <p class="section">Recents</p>

        <ul class="convs">
          @for (c of conversations(); track c.id) {
            <li class="conv" [class.active]="c.id === activeId()" (click)="open(c.id)">
              <span class="conv-title" [matTooltip]="c.title ?? ''">{{ c.title || 'Untitled' }}</span>
              <button class="icon del" aria-label="Delete conversation"
                      (click)="$event.stopPropagation(); confirmDelete(c)">
                <mat-icon>delete_outline</mat-icon>
              </button>
            </li>
          } @empty {
            <li class="muted empty-note">No conversations yet.</li>
          }
        </ul>
      </aside>

      <section class="chat">
        @if (loadingHistory()) { <mat-progress-bar mode="indeterminate" /> }

        <div class="messages" #scroller>
          <div class="thread">
            @for (m of messages(); track m.id) {
              <article class="msg" [class.user]="m.role === 'user'">
                @if (m.role === 'assistant') {
                  <span class="who"><span class="dot"></span>Assistant</span>
                }
                <div class="bubble">{{ m.content }}</div>

                @if (m.citations.length) {
                  <div class="sources">
                    <span class="muted">Sources</span>
                    @for (s of m.citations; track s.chunkId; let i = $index) {
                      <span class="chip"
                            [matTooltip]="'chunk #' + s.chunkIndex + ' · score ' + (s.score | number: '1.2-2')">
                        <b>{{ i + 1 }}</b>{{ s.title }}
                      </span>
                    }
                  </div>
                }
              </article>
            } @empty {
              @if (!loadingHistory()) {
                <div class="welcome">
                  <h2>How can I help today?</h2>
                  <p class="muted">Ask anything about your organization's documents.</p>
                </div>
              }
            }

            @if (sending()) {
              <article class="msg">
                <span class="who"><span class="dot"></span>Assistant</span>
                <div class="bubble thinking"><i></i><i></i><i></i></div>
              </article>
            }
          </div>
        </div>

        <div class="composer-wrap">
          <form class="composer" (ngSubmit)="send()">
            <textarea name="q" rows="1" [(ngModel)]="draft" [maxlength]="maxQuestion"
                      placeholder="Ask anything about your documents…"
                      (input)="autoGrow($event)"
                      (keydown.enter)="onEnter($event)" [disabled]="sending()"></textarea>
            <button class="send" type="submit" [disabled]="sending() || !draft.trim()" aria-label="Send">
              <mat-icon>arrow_upward</mat-icon>
            </button>
          </form>
          <p class="hint muted">Enter to send · Shift+Enter for a new line</p>
        </div>
      </section>
    </div>
  `,
  styles: `
    :host { display: block; height: 100%; }

    .layout { display: flex; height: 100%; }

    /* ---------- Sidebar (flush left, no outer gap) ---------- */
    .sidebar {
      width: var(--sidebar-w);
      flex: 0 0 var(--sidebar-w);
      background: var(--bg);
      border-right: 1px solid var(--border);
      padding: 12px 10px;
      overflow-y: auto;
    }

    .new {
      display: flex; align-items: center; gap: 8px;
      width: 100%; padding: 9px 12px;
      background: var(--surface); color: var(--text);
      border: 1px solid var(--border); border-radius: 10px;
      font: inherit; font-weight: 500; cursor: pointer;
      transition: background .15s, border-color .15s;
    }
    .new:hover { background: var(--surface-sunken); border-color: #d3cfc2; }
    .new mat-icon { font-size: 18px; width: 18px; height: 18px; color: var(--accent); }

    .section {
      margin: 18px 6px 6px;
      font-size: 11px; font-weight: 600; letter-spacing: .06em;
      text-transform: uppercase; color: var(--text-muted);
    }

    .convs { list-style: none; margin: 0; padding: 0; }
    .conv {
      display: flex; align-items: center; gap: 4px;
      padding: 7px 8px 7px 10px; border-radius: 8px;
      cursor: pointer; color: var(--text-muted); font-size: 14px;
      transition: background .15s, color .15s;
    }
    .conv:hover { background: var(--surface-sunken); color: var(--text); }
    .conv.active { background: var(--accent-soft); color: var(--accent); font-weight: 500; }
    .conv-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .empty-note { padding: 6px 10px; font-size: 13px; }

    .icon {
      display: grid; place-items: center;
      background: none; border: 0; padding: 2px; border-radius: 6px;
      color: inherit; cursor: pointer; opacity: 0; transition: opacity .15s, background .15s;
    }
    .conv:hover .icon, .conv.active .icon { opacity: .6; }
    .icon:hover { opacity: 1; background: rgba(0,0,0,.06); }
    .icon mat-icon { font-size: 17px; width: 17px; height: 17px; }

    /* ---------- Chat column ---------- */
    .chat {
      flex: 1; min-width: 0;
      display: flex; flex-direction: column;
      background: var(--surface);
    }

    .messages { flex: 1; overflow-y: auto; }
    .thread { max-width: 760px; margin: 0 auto; padding: 28px 24px 8px; }

    .msg { display: flex; flex-direction: column; margin-bottom: 26px; }
    .msg.user { align-items: flex-end; }

    .who {
      display: inline-flex; align-items: center; gap: 6px;
      font-size: 12px; font-weight: 600; color: var(--text-muted);
      margin-bottom: 6px;
    }
    .who .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); }

    .bubble {
      white-space: pre-wrap;
      line-height: 1.65;
      border-radius: var(--radius);
    }
    /* Assistant reads like a document — no heavy bubble. */
    .msg:not(.user) .bubble { background: none; padding: 0; }
    /* User gets a soft chip aligned right. */
    .msg.user .bubble {
      background: var(--surface-sunken);
      border: 1px solid var(--border);
      padding: 10px 14px;
      max-width: 85%;
    }

    .sources {
      display: flex; flex-wrap: wrap; align-items: center; gap: 6px;
      margin-top: 12px; padding-top: 10px;
      border-top: 1px solid var(--border);
      font-size: 12px;
    }
    .chip {
      display: inline-flex; align-items: center; gap: 6px;
      background: var(--surface-sunken); color: var(--text-muted);
      border: 1px solid var(--border); border-radius: 999px;
      padding: 3px 10px 3px 4px; cursor: default;
    }
    .chip b {
      display: grid; place-items: center;
      width: 17px; height: 17px; border-radius: 50%;
      background: var(--accent); color: var(--on-accent); font-size: 10px;
    }

    .welcome { text-align: center; margin-top: 18vh; }
    .welcome h2 { font-size: 26px; margin-bottom: 6px; }

    /* typing dots */
    .thinking { display: inline-flex; gap: 5px; padding: 6px 0; }
    .thinking i {
      width: 7px; height: 7px; border-radius: 50%; background: var(--text-muted);
      animation: blink 1.3s infinite ease-in-out;
    }
    .thinking i:nth-child(2) { animation-delay: .18s; }
    .thinking i:nth-child(3) { animation-delay: .36s; }
    @keyframes blink { 0%, 80%, 100% { opacity: .25; } 40% { opacity: 1; } }

    /* ---------- Composer ---------- */
    .composer-wrap { padding: 8px 24px 16px; }
    .composer {
      display: flex; align-items: flex-end; gap: 8px;
      max-width: 760px; margin: 0 auto;
      background: var(--bg);
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      padding: 10px 10px 10px 16px;
      box-shadow: var(--shadow);
      transition: border-color .15s;
    }
    .composer:focus-within { border-color: #cbb6ac; }
    .composer textarea {
      flex: 1; resize: none; border: 0; outline: 0; background: transparent;
      font: inherit; color: var(--text); line-height: 1.55;
      max-height: 180px; padding: 4px 0;
    }
    .composer textarea::placeholder { color: var(--text-muted); }

    .send {
      display: grid; place-items: center;
      width: 34px; height: 34px; flex: none;
      border: 0; border-radius: 9px; cursor: pointer;
      background: var(--accent); color: var(--on-accent);
      transition: background .15s, opacity .15s;
    }
    .send:hover:not(:disabled) { background: var(--accent-hover); }
    .send:disabled { background: var(--surface-sunken); color: var(--text-muted); cursor: default; }
    .send mat-icon { font-size: 19px; width: 19px; height: 19px; }

    .hint { max-width: 760px; margin: 6px auto 0; font-size: 11px; text-align: center; }
    .muted { color: var(--text-muted); }

    @media (max-width: 900px) {
      .sidebar { display: none; }
    }
  `,
})
export class ChatComponent implements OnInit {
  private readonly api = inject(ChatService);
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  readonly maxQuestion = MAX_QUESTION;
  readonly conversations = signal<ConversationSummary[]>([]);
  readonly messages = signal<ChatMessage[]>([]);
  readonly activeId = signal<string | null>(null);
  readonly sending = signal(false);
  readonly loadingHistory = signal(false);
  draft = '';

  ngOnInit(): void {
    this.refreshList();
    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.open(id);
  }

  refreshList(): void {
    this.api.conversations().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (list) => this.conversations.set(list),
      error: (e) => this.toast(this.message(e, 'Failed to load conversations')),
    });
  }

  newChat(): void {
    this.activeId.set(null);
    this.messages.set([]);
    this.location.replaceState('/chat');
  }

  open(id: string): void {
    if (this.sending()) return;
    this.activeId.set(id);
    this.loadingHistory.set(true);
    // Update the URL without re-creating the component (deep-linkable).
    this.location.replaceState(`/chat/${id}`);
    this.api.messages(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (msgs) => { this.messages.set(msgs); this.loadingHistory.set(false); this.scrollDown(); },
      error: (e) => {
        this.loadingHistory.set(false);
        this.toast(this.message(e, 'Conversation not found'));
        this.newChat();
      },
    });
  }

  onEnter(e: Event): void {
    const ke = e as KeyboardEvent;
    if (ke.shiftKey) return; // newline
    ke.preventDefault();
    this.send();
  }

  /** Grow the composer with the text, up to the CSS max-height. */
  autoGrow(e: Event): void {
    const el = e.target as HTMLTextAreaElement;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }

  send(): void {
    const question = this.draft.trim();
    if (!question || this.sending()) return;

    // Optimistic user bubble.
    const tempId = `tmp-${Date.now()}`;
    this.messages.update((m) => [...m, {
      id: tempId, role: 'user', content: question, citations: [], createdAt: new Date().toISOString(),
    }]);
    this.draft = '';
    this.sending.set(true);
    this.scrollDown();

    this.api.ask({ question, conversationId: this.activeId() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.sending.set(false);
          this.messages.update((m) => [...m, {
            id: res.messageId, role: 'assistant', content: res.answer,
            citations: res.citations ?? [], createdAt: new Date().toISOString(),
          }]);
          if (this.activeId() !== res.conversationId) {
            this.activeId.set(res.conversationId);
            this.location.replaceState(`/chat/${res.conversationId}`);
          }
          this.refreshList(); // new title / reorder by updated_at
          this.scrollDown();
        },
        error: (e) => {
          this.sending.set(false);
          // Roll back the optimistic bubble and restore the draft so nothing is lost.
          this.messages.update((m) => m.filter((x) => x.id !== tempId));
          this.draft = question;
          this.toast(this.message(e, 'Failed to get an answer'));
        },
      });
  }

  confirmDelete(c: ConversationSummary): void {
    const data: ConfirmDialogData = {
      title: 'Delete conversation?',
      message: `“${c.title || 'Untitled'}” and all its messages will be removed.`,
      confirmLabel: 'Delete',
    };
    this.dialog.open(ConfirmDialogComponent, { data, width: '420px' }).afterClosed()
      .pipe(filter(Boolean), switchMap(() => this.api.deleteConversation(c.id)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          if (this.activeId() === c.id) this.newChat();
          this.refreshList();
        },
        error: (e) => this.toast(this.message(e, 'Delete failed')),
      });
  }

  private scrollDown(): void {
    setTimeout(() => {
      const el = this.scroller()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

  private message(e: unknown, fallback: string): string {
    if (e instanceof HttpErrorResponse) {
      if (e.status === 503) return 'The AI service is busy (rate limit). Please try again in a moment.';
      return e.error?.message ?? fallback;
    }
    return fallback;
  }

  private toast(msg: string): void {
    this.snack.open(msg, 'OK', { duration: 5000 });
  }
}
