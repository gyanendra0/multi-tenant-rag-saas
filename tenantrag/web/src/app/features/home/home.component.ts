import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

import { AuthService } from '../../core/auth/auth.service';

/** Placeholder landing page — replaced by Documents in Step 2. */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>Welcome, {{ auth.user()?.fullName }}</mat-card-title>
        <mat-card-subtitle>{{ auth.user()?.tenantName }} ({{ auth.user()?.tenantSlug }})</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <p>You're signed in. Documents and Chat arrive in the next steps.</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class HomeComponent {
  readonly auth = inject(AuthService);
}
