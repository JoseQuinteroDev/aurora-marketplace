import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

// Module-scoped single-flight: concurrent 401s collapse into ONE /api/auth/refresh
// call, then all retry. Reset via finalize when the refresh settles.
let refreshInFlight: Observable<unknown> | null = null;

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.getToken();

  const authorized = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(authorized).pipe(
    catchError((error: HttpErrorResponse) => {
      // The login/register/refresh/logout calls return 401 on their own terms —
      // that's not an expired session, so let those pages surface it (never refresh-loop).
      const isAuthCall = request.url.includes('/api/auth/');

      const bailToLogin = () => {
        auth.logout(null);
        const returnUrl = router.url;
        router.navigate(['/login'], {
          queryParams: returnUrl && !returnUrl.startsWith('/login') ? { returnUrl } : {}
        });
        return throwError(() => error);
      };

      if (error.status === 401 && !isAuthCall) {
        // A request already retried once (X-Retried) that is STILL 401 means the
        // fresh token is no good either — stop, don't loop. No refresh token → bail.
        if (request.headers.has('X-Retried') || !auth.getRefreshToken()) {
          return bailToLogin();
        }

        if (!refreshInFlight) {
          refreshInFlight = auth.refresh().pipe(
            finalize(() => { refreshInFlight = null; }),
            shareReplay({ bufferSize: 1, refCount: true })
          );
        }

        return refreshInFlight.pipe(
          switchMap(() => next(request.clone({
            setHeaders: { Authorization: `Bearer ${auth.getToken()}`, 'X-Retried': '1' }
          }))),
          catchError(() => bailToLogin())
        );
      }

      if (error.status === 403 && !isAuthCall) {
        router.navigateByUrl('/');
      }

      return throwError(() => error);
    })
  );
};
