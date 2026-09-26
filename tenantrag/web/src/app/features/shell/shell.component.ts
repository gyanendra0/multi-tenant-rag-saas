import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Authenticated app shell: top bar with tenant + user and logout.
 * Documents (Step 2) and Chat (Step 3) will render inside the outlet.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, MatToolbarModule, MatButtonModule, MatIconModule],
  template: `
    <mat-toolbar color="primary">
      <span>{{ auth.user()?.tenantName ?? 'Tenant RAG' }}</span>
      <span class="spacer"></span>
      <span class="who">{{ auth.user()?.fullName }} · {{ auth.user()?.role }}</span>
      <button mat-icon-button (click)="auth.logout()" aria-label="Sign out">
        <mat-icon>logout</mat-icon>
      </button>
    </mat-toolbar>

    <main class="content">
      <router-outlet />
    </main>
  `,
  styles: `
    .spacer { flex: 1; }
    .who { font-size: 14px; margin-right: 8px; opacity: .85; }
    .content { padding: 24px; max-width: 1100px; margin: 0 auto; }
  `,
})
export class ShellComponent {
  readonly auth = inject(AuthService);
}
