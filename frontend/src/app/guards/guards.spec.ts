import { TestBed } from '@angular/core/testing';
import { Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { adminGuard } from './admin.guard';
import { authGuard } from './auth.guard';

/**
 * Route-protection guards (frontend half of access control). These are defense in
 * depth — the backend is always authoritative — but they keep unauthenticated and
 * non-admin users out of protected routes and preserve the returnUrl.
 */
describe('route guards', () => {
  let authenticated: boolean;
  let admin: boolean;
  let urlTrees: { commands: unknown[]; extras?: unknown }[];

  function run(guard: typeof authGuard, url: string) {
    return TestBed.runInInjectionContext(() => guard({} as never, { url } as RouterStateSnapshot));
  }

  beforeEach(() => {
    authenticated = false;
    admin = false;
    urlTrees = [];
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => authenticated, isAdmin: () => admin } },
        {
          provide: Router,
          useValue: {
            createUrlTree: (commands: unknown[], extras?: unknown) => {
              const tree = { commands, extras };
              urlTrees.push(tree);
              return tree;
            },
          },
        },
      ],
    });
  });

  it('authGuard allows an authenticated user', () => {
    authenticated = true;
    expect(run(authGuard, '/orders')).toBe(true);
  });

  it('authGuard redirects an anonymous user to /login with the returnUrl', () => {
    authenticated = false;
    const result = run(authGuard, '/orders');
    expect(result).toBe(urlTrees[0]);
    expect(urlTrees[0].commands).toEqual(['/login']);
    expect(urlTrees[0].extras).toEqual({ queryParams: { returnUrl: '/orders' } });
  });

  it('adminGuard allows an authenticated admin', () => {
    authenticated = true;
    admin = true;
    expect(run(adminGuard, '/admin')).toBe(true);
  });

  it('adminGuard sends a non-admin to the home page', () => {
    authenticated = true;
    admin = false;
    const result = run(adminGuard, '/admin');
    expect(result).toBe(urlTrees[0]);
    expect(urlTrees[0].commands).toEqual(['/']);
  });

  it('adminGuard sends an anonymous user to /login with the returnUrl', () => {
    authenticated = false;
    run(adminGuard, '/admin');
    expect(urlTrees[0].commands).toEqual(['/login']);
    expect(urlTrees[0].extras).toEqual({ queryParams: { returnUrl: '/admin' } });
  });
});
