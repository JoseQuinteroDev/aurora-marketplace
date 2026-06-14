import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.getToken();

  const authorized = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(authorized).pipe(
    catchError((error: HttpErrorResponse) => {
      // The login/register calls return 401 on bad credentials — that's not an
      // expired session, so let those pages surface their own error message.
      const isAuthCall = request.url.includes('/api/auth/');

      if (error.status === 401 && !isAuthCall) {
        auth.logout(null);
        const returnUrl = router.url;
        router.navigate(['/login'], {
          queryParams: returnUrl && !returnUrl.startsWith('/login') ? { returnUrl } : {}
        });
      } else if (error.status === 403 && !isAuthCall) {
        router.navigateByUrl('/');
      }

      return throwError(() => error);
    })
  );
};
