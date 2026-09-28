import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Authenticated shell: a slim top bar + full-bleed content area.
 *
 * <p>The content area has **no padding or max-width** so pages can go
 * edge-to-edge (the chat sidebar must sit flush against the left edge).
 * Pages that want a centered column add `.page` themselves.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <header class="topbar">
      <span class="brand">
        <span class="dot"></span>{{ auth.user()?.tenantName ?? 'TenantRAG' }}
      </span>

      <nav class="nav">
        <a routerLink="/chat" routerLinkActive="active">
          <mat-icon>forum</mat-icon><span>Chat</span>
        </a>
        <a routerLink="/documents" routerLinkActive="active">
          <mat-icon>description</mat-icon><span>Documents</span>
        </a>
      </nav>

      <span class="spacer"></span>

      <button class="avatar-btn" [matMenuTriggerFor]="menu" aria-label="Account">
        <span class="avatar">{{ initials() }}</span>
      </button>
      <mat-menu #menu="matMenu">
        <div class="menu-head">
          <strong>{{ auth.user()?.fullName }}</strong>
          <small>{{ auth.user()?.email }}</small>
          <small class="role">{{ auth.user()?.role }}</small>
        </div>
        <button mat-menu-item (click)="auth.logout()">
          <mat-icon>logout</mat-icon><span>Sign out</span>
        </button>
      </mat-menu>
    </header>

    <main class="content">
      <router-outlet />
    </main>
  `,
  styles: `
    :host { display: block; }

    .topbar {
      height: var(--topbar-h);
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 0 14px;
      background: var(--bg);
      border-bottom: 1px solid var(--border);
      position: sticky;
      top: 0;
      z-index: 10;
    }

    .brand {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      font-weight: 600;
      letter-spacing: -.01em;
      margin-right: 10px;
    }
    .dot {
      width: 14px; height: 14px; border-radius: 4px;
      background: var(--accent);
      box-shadow: inset 0 0 0 3px rgba(255,255,255,.55);
    }

    .nav { display: flex; gap: 2px; }
    .nav a {
      display: inline-flex; align-items: center; gap: 6px;
      padding: 6px 12px; border-radius: 9px;
      color: var(--text-muted); text-decoration: none;
      font-size: 14px; font-weight: 500;
      transition: background .15s, color .15s;
    }
    .nav a:hover { background: var(--surface-sunken); color: var(--text); }
    .nav a.active { background: var(--accent-soft); color: var(--accent); }
    .nav mat-icon { font-size: 19px; width: 19px; height: 19px; }

    .spacer { flex: 1; }

    .avatar-btn { background: none; border: 0; padding: 0; cursor: pointer; }
    .avatar {
      display: grid; place-items: center;
      width: 32px; height: 32px; border-radius: 50%;
      background: var(--accent); color: var(--on-accent);
      font-size: 13px; font-weight: 600;
    }

    .menu-head {
      display: flex; flex-direction: column; gap: 2px;
      padding: 10px 16px 12px;
      border-bottom: 1px solid var(--border);
    }
    .menu-head small { color: var(--text-muted); }
    .menu-head .role { font-size: 11px; text-transform: uppercase; letter-spacing: .05em; }

    /* Full-bleed: pages control their own padding. */
    .content { height: calc(100vh - var(--topbar-h)); overflow: hidden; }
  `,
})
export class ShellComponent {
  readonly auth = inject(AuthService);

  initials(): string {
    const name = this.auth.user()?.fullName?.trim();
    if (!name) return '?';
    const parts = name.split(/\s+/);
    return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  }
}
