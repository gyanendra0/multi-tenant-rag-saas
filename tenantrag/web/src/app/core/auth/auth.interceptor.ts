import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/** Auth endpoints never get a Bearer header and never trigger a refresh. */
const AUTH_PATHS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/logout'];

function isAuthCall(req: HttpRequest<unknown>): boolean {
  return AUTH_PATHS.some((p) => req.url.startsWith(p));
}

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/**
 * Adds the Bearer access token. On a 401 from a normal API call, refreshes once
 * (via the httpOnly cookie) and retries the request with the new token. If the
 * refresh fails, the session is over → go to login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  if (isAuthCall(req)) {
    return next(req);
  }

  return next(withToken(req, auth.accessToken)).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401) {
        return throwError(() => err);
      }
      return auth.refresh().pipe(
        switchMap((token) => {
          if (!token) {
            auth.sessionExpired();
            return throwError(() => err);
          }
          return next(withToken(req, token));
        }),
      );
    }),
  );
};
