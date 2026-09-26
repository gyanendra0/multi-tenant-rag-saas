import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap } from 'rxjs';

import { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from './auth.models';

/**
 * Holds the session.
 *
 * - Access token: kept in MEMORY only (a signal) — lost on reload by design.
 * - Refresh token: lives in an httpOnly cookie set by the backend; JS never
 *   sees it. On reload we call /refresh and the browser sends the cookie.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _accessToken = signal<string | null>(null);
  private readonly _user = signal<UserResponse | null>(null);

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._accessToken() !== null);

  /** Single in-flight refresh shared by concurrent 401s. */
  private refreshInFlight$: Observable<string | null> | null = null;

  get accessToken(): string | null {
    return this._accessToken();
  }

  login(req: LoginRequest): Observable<UserResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', req).pipe(
      tap((res) => this.setSession(res)),
      map((res) => res.user),
    );
  }

  /** Register returns only a profile (no tokens), so log in right after. */
  register(req: RegisterRequest): Observable<UserResponse> {
    return this.http
      .post<UserResponse>('/api/auth/register', req)
      .pipe(switchMap(() => this.login({ email: req.email, password: req.password })));
  }

  /**
   * Exchange the refresh cookie for a new access token. Emits the new token, or
   * null if the session is gone. Never errors.
   */
  refresh(): Observable<string | null> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.http.post<AuthResponse>('/api/auth/refresh', null).pipe(
        tap((res) => this.setSession(res)),
        map((res) => res.accessToken),
        catchError(() => {
          this.clearSession();
          return of(null);
        }),
        finalize(() => (this.refreshInFlight$ = null)),
        shareReplay(1),
      );
    }
    return this.refreshInFlight$;
  }

  logout(): void {
    this.http
      .post<void>('/api/auth/logout', null)
      .pipe(catchError(() => of(null)))
      .subscribe(() => {
        this.clearSession();
        this.router.navigate(['/login']);
      });
  }

  /** Called when refresh fails mid-session. */
  sessionExpired(): void {
    this.clearSession();
    this.router.navigate(['/login']);
  }

  private setSession(res: AuthResponse): void {
    this._accessToken.set(res.accessToken);
    this._user.set(res.user);
  }

  private clearSession(): void {
    this._accessToken.set(null);
    this._user.set(null);
  }
}
