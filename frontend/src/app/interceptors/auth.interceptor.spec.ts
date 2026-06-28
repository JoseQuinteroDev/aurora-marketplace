import {
  HttpErrorResponse,
  HttpHandlerFn,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let token: string | null;
  let refreshToken: string | null;
  let refreshCalls: number;
  let refreshResult: () => Observable<unknown>;
  let logoutArgs: (string | null)[];
  let navigateCalls: { commands: unknown[]; extras?: unknown }[];
  let navigateByUrlCalls: string[];

  beforeEach(() => {
    token = 'access-tok';
    refreshToken = null;
    refreshCalls = 0;
    refreshResult = () => of(null);
    logoutArgs = [];
    navigateCalls = [];
    navigateByUrlCalls = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            getToken: () => token,
            getRefreshToken: () => refreshToken,
            logout: (arg: string | null) => logoutArgs.push(arg),
            refresh: () => {
              refreshCalls++;
              return refreshResult();
            },
          },
        },
        {
          provide: Router,
          useValue: {
            url: '/cart',
            navigate: (commands: unknown[], extras?: unknown) => navigateCalls.push({ commands, extras }),
            navigateByUrl: (u: string) => navigateByUrlCalls.push(u),
          },
        },
      ],
    });
  });

  function intercept(req: HttpRequest<unknown>, next: HttpHandlerFn) {
    return TestBed.runInInjectionContext(() => authInterceptor(req, next));
  }

  it('attaches the Bearer token when one is present', () => {
    let seen: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (r) => {
      seen = r;
      return of(new HttpResponse({ status: 200 }));
    };

    intercept(new HttpRequest('GET', '/api/cart'), next).subscribe();

    expect(seen?.headers.get('Authorization')).toBe('Bearer access-tok');
  });

  it('does not attach an Authorization header when there is no token', () => {
    token = null;
    let seen: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (r) => {
      seen = r;
      return of(new HttpResponse({ status: 200 }));
    };

    intercept(new HttpRequest('GET', '/api/cart'), next).subscribe();

    expect(seen?.headers.has('Authorization')).toBe(false);
  });

  it('on a 401 with no refresh token it logs out and redirects to /login', () => {
    refreshToken = null;
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 401 }));

    intercept(new HttpRequest('GET', '/api/orders'), next).subscribe({ error: () => {} });

    expect(refreshCalls).toBe(0);
    expect(logoutArgs).toEqual([null]);
    expect(navigateCalls[0]?.commands).toEqual(['/login']);
  });

  it('on a 401 with a refresh token it refreshes once and retries with the new bearer', () => {
    refreshToken = 'rid.secret';
    refreshResult = () => {
      token = 'new-tok'; // simulate the rotated access token being persisted
      return of(null);
    };
    let retried: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (r) => {
      if (r.headers.has('X-Retried')) {
        retried = r;
        return of(new HttpResponse({ status: 200 }));
      }
      return throwError(() => new HttpErrorResponse({ status: 401 }));
    };

    let okStatus: number | undefined;
    intercept(new HttpRequest('GET', '/api/orders'), next).subscribe({
      next: (event) => {
        if (event instanceof HttpResponse) okStatus = event.status;
      },
      error: () => {},
    });

    expect(refreshCalls).toBe(1);
    expect(retried?.headers.get('Authorization')).toBe('Bearer new-tok');
    expect(okStatus).toBe(200);
    expect(logoutArgs).toEqual([]); // never logged out
  });

  it('when the refresh itself fails it logs out and redirects to /login', () => {
    refreshToken = 'rid.secret';
    refreshResult = () => throwError(() => new Error('refresh failed'));
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 401 }));

    intercept(new HttpRequest('GET', '/api/orders'), next).subscribe({ error: () => {} });

    expect(refreshCalls).toBe(1);
    expect(logoutArgs).toEqual([null]);
    expect(navigateCalls[0]?.commands).toEqual(['/login']);
  });

  it('on a 403 (non-auth call) it redirects home without logging out', () => {
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 403 }));

    intercept(new HttpRequest('GET', '/api/admin/orders'), next).subscribe({ error: () => {} });

    expect(logoutArgs).toEqual([]);
    expect(navigateByUrlCalls).toEqual(['/']);
  });

  it('a 401 from an /api/auth/ call is left for the page (no refresh, no logout)', () => {
    refreshToken = 'rid.secret';
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 401 }));

    intercept(new HttpRequest('POST', '/api/auth/login', {}), next).subscribe({ error: () => {} });

    expect(refreshCalls).toBe(0);
    expect(logoutArgs).toEqual([]);
    expect(navigateCalls).toEqual([]);
  });
});
