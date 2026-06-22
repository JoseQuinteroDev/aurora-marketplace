import {
  HttpErrorResponse,
  HttpHandlerFn,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let token: string | null;
  let logoutArgs: (string | null)[];
  let navigateCalls: { commands: unknown[]; extras?: unknown }[];
  let navigateByUrlCalls: string[];

  beforeEach(() => {
    token = null;
    logoutArgs = [];
    navigateCalls = [];
    navigateByUrlCalls = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { getToken: () => token, logout: (arg: string | null) => logoutArgs.push(arg) },
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
    token = 'tok-123';
    let seen: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (r) => {
      seen = r;
      return of(new HttpResponse({ status: 200 }));
    };

    intercept(new HttpRequest('GET', '/api/cart'), next).subscribe();

    expect(seen?.headers.get('Authorization')).toBe('Bearer tok-123');
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

  it('on a 401 (non-auth call) it logs out and redirects to /login', () => {
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 401 }));

    intercept(new HttpRequest('GET', '/api/orders'), next).subscribe({ error: () => {} });

    expect(logoutArgs).toEqual([null]);
    expect(navigateCalls[0]?.commands).toEqual(['/login']);
  });

  it('on a 403 (non-auth call) it redirects home without logging out', () => {
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 403 }));

    intercept(new HttpRequest('GET', '/api/admin/orders'), next).subscribe({ error: () => {} });

    expect(logoutArgs).toEqual([]);
    expect(navigateByUrlCalls).toEqual(['/']);
  });

  it('a 401 from an /api/auth/ call is left for the page to handle', () => {
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 401 }));

    intercept(new HttpRequest('POST', '/api/auth/login', {}), next).subscribe({ error: () => {} });

    expect(logoutArgs).toEqual([]);
    expect(navigateCalls).toEqual([]);
  });
});
