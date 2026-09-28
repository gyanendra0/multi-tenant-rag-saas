import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

/** Same slug rule as the backend's RegisterRequest @Pattern. */
const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    ReactiveFormsModule, RouterLink,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressBarModule,
  ],
  styleUrl: './auth-page.scss',
  template: `
    <div class="auth-page">
      <div class="auth-card">
        @if (loading()) { <mat-progress-bar mode="indeterminate" /> }

        <div class="auth-brand"><span class="dot"></span>TenantRAG</div>
        <h1>Create your organization</h1>
        <p class="subtitle">You'll be its owner. Everything you upload stays private to it.</p>

        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field appearance="outline">
            <mat-label>Organization name</mat-label>
            <input matInput formControlName="tenantName" (input)="suggestSlug()" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Organization slug</mat-label>
            <input matInput formControlName="tenantSlug" (input)="slugTouched = true" />
            <mat-hint>lowercase letters, numbers, dashes — e.g. acme-corp</mat-hint>
            @if (form.controls.tenantSlug.hasError('pattern')) {
              <mat-error>Only lowercase letters, numbers and single dashes.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Your full name</mat-label>
            <input matInput formControlName="fullName" autocomplete="name" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Password</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="new-password" />
            <mat-hint>At least 8 characters</mat-hint>
          </mat-form-field>

          @if (error()) { <p class="error">{{ error() }}</p> }

          <button mat-flat-button color="primary" type="submit"
                  [disabled]="form.invalid || loading()">Create organization</button>
        </form>

        <p class="alt">Already have an account? <a routerLink="/login">Sign in</a></p>
      </div>
    </div>
  `,
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  slugTouched = false;

  readonly form = inject(FormBuilder).nonNullable.group({
    tenantName: ['', Validators.required],
    tenantSlug: ['', [Validators.required, Validators.pattern(SLUG_PATTERN)]],
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  /** Auto-fill the slug from the org name until the user edits it. */
  suggestSlug(): void {
    if (this.slugTouched) return;
    const slug = this.form.controls.tenantName.value
      .toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    this.form.controls.tenantSlug.setValue(slug);
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);

    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => this.router.navigate(['/']),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 409 ? (err.error?.message ?? 'Email or slug already in use.')
          : err.status === 400 ? 'Please check the form fields.'
          : 'Registration failed. Is the backend running?',
        );
      },
    });
  }
}
