import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthPayload } from '../core/models/auth.model';
import { AuthService } from './auth.service';

const TOKEN_KEY = 'aurora_access_token';
const EXPIRY_KEY = 'aurora_token_expires_at';

function payload(role: 'CUSTOMER' | 'ADMIN' = 'CUSTOMER', minutes = 60): AuthPayload {
  return {
    tokenType: 'Bearer',
    accessToken: 'tok-123',
    refreshToken: 'rid-123.secret',
    expiresInMinutes: minutes,
    user: { id: 'u1', email: 'a@b.c', firstName: 'A', lastName: 'B', role },
    status: 'AUTHENTICATED',
  };
}

// An MFA challenge response: NO tokens, just the opaque mfaToken.
function mfaChallenge(): AuthPayload {
  return {
    tokenType: 'Bearer',
    accessToken: null as unknown as string,
    refreshToken: null as unknown as string,
    expiresInMinutes: 0,
    user: null as unknown as AuthPayload['user'],
    status: 'MFA_REQUIRED',
    mfaToken: 'mfa-opaque-token',
  };
}

describe('AuthService', () => {
  let navigations: string[];

  function setup() {
    navigations = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { url: '/', navigateByUrl: (u: string) => navigations.push(u) } },
      ],
    });
    return {
      service: TestBed.inject(AuthService),
      http: TestBed.inject(HttpTestingController),
    };
  }

  beforeEach(() => localStorage.clear());

  it('starts unauthenticated when there is no token', () => {
    const { service } = setup();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.getToken()).toBeNull();
    expect(service.isAdmin()).toBe(false);
  });

  it('login persists the session and authenticates', () => {
    const { service, http } = setup();

    service.login({ email: 'a@b.c', password: 'secret12' }).subscribe();
    http.expectOne('/api/auth/login').flush({ data: payload('CUSTOMER') });

    expect(service.isAuthenticated()).toBe(true);
    expect(service.getToken()).toBe('tok-123');
    expect(service.currentUser()?.email).toBe('a@b.c');
    http.verify();
  });

  it('isAdmin is true only for an ADMIN role on a live session', () => {
    const { service, http } = setup();

    service.login({ email: 'a@b.c', password: 'secret12' }).subscribe();
    http.expectOne('/api/auth/login').flush({ data: payload('ADMIN') });

    expect(service.isAdmin()).toBe(true);
    http.verify();
  });

  it('treats a token past its stored expiry as logged out (cleared on boot)', () => {
    localStorage.setItem(TOKEN_KEY, 'stale');
    localStorage.setItem(EXPIRY_KEY, String(Date.now() - 1000));

    const { service } = setup();   // constructor clears the stale session

    expect(service.isAuthenticated()).toBe(false);
    expect(service.getToken()).toBeNull();
  });

  it('logout clears the session and navigates home', () => {
    const { service, http } = setup();
    service.login({ email: 'a@b.c', password: 'secret12' }).subscribe();
    http.expectOne('/api/auth/login').flush({ data: payload() });

    service.logout();
    const logoutReq = http.expectOne('/api/auth/logout');
    expect(logoutReq.request.body.refreshToken).toBe('rid-123.secret'); // family revoke
    logoutReq.flush({});

    expect(service.getToken()).toBeNull();
    expect(service.getRefreshToken()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(navigations).toContain('/');
    http.verify();
  });

  it('refresh rotates the stored access + refresh tokens in place', () => {
    const { service, http } = setup();
    service.login({ email: 'a@b.c', password: 'secret12' }).subscribe();
    http.expectOne('/api/auth/login').flush({ data: payload() });
    expect(service.getRefreshToken()).toBe('rid-123.secret');

    service.refresh().subscribe();
    const req = http.expectOne('/api/auth/refresh');
    expect(req.request.body.refreshToken).toBe('rid-123.secret');
    req.flush({ data: { ...payload(), accessToken: 'tok-456', refreshToken: 'rid-456.secret' } });

    expect(service.getToken()).toBe('tok-456');
    expect(service.getRefreshToken()).toBe('rid-456.secret');
    http.verify();
  });

  it('requestPasswordReset posts the email and never touches the session', () => {
    const { service, http } = setup();

    service.requestPasswordReset('a@b.c').subscribe();
    const req = http.expectOne('/api/auth/forgot-password');
    expect(req.request.body).toEqual({ email: 'a@b.c' });
    req.flush({ data: null });

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull(); // does not authenticate
    http.verify();
  });

  it('resetPassword posts token + newPassword and does not authenticate', () => {
    const { service, http } = setup();

    service.resetPassword('rid.secret', 'Password123!').subscribe();
    const req = http.expectOne('/api/auth/reset-password');
    expect(req.request.body).toEqual({ token: 'rid.secret', newPassword: 'Password123!' });
    req.flush({ data: null });

    expect(service.getToken()).toBeNull();
    expect(service.getRefreshToken()).toBeNull();
    http.verify();
  });

  it('verifyEmail posts the token and never touches the session', () => {
    const { service, http } = setup();

    service.verifyEmail('ev.secret').subscribe();
    const req = http.expectOne('/api/auth/verify-email');
    expect(req.request.body).toEqual({ token: 'ev.secret' });
    req.flush({ data: null });

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull(); // does not authenticate
    http.verify();
  });

  it('resendVerification posts the email and does not authenticate', () => {
    const { service, http } = setup();

    service.resendVerification('a@b.c').subscribe();
    const req = http.expectOne('/api/auth/resend-verification');
    expect(req.request.body).toEqual({ email: 'a@b.c' });
    req.flush({ data: null });

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    http.verify();
  });

  it('login with an MFA_REQUIRED response does NOT persist a session and returns the challenge', () => {
    const { service, http } = setup();

    let received: { status?: string; mfaToken?: string | null } | undefined;
    service.login({ email: 'a@b.c', password: 'secret12' }).subscribe((r) => {
      received = r.data;
    });
    http.expectOne('/api/auth/login').flush({ data: mfaChallenge() });

    // No tokens stored, not authenticated — the session is established only after /mfa/verify.
    expect(service.getToken()).toBeNull();
    expect(service.getRefreshToken()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    // The challenge is handed back so the login page can switch to the code step.
    expect(received?.status).toBe('MFA_REQUIRED');
    expect(received?.mfaToken).toBe('mfa-opaque-token');
    http.verify();
  });

  it('mfaVerify posts {mfaToken, code} and persists the session exactly like a normal login', () => {
    const { service, http } = setup();

    service.mfaVerify('mfa-opaque-token', '123456').subscribe();
    const req = http.expectOne('/api/auth/mfa/verify');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ mfaToken: 'mfa-opaque-token', code: '123456' });
    req.flush({ data: payload('CUSTOMER') });

    expect(service.isAuthenticated()).toBe(true);
    expect(service.getToken()).toBe('tok-123');
    expect(service.getRefreshToken()).toBe('rid-123.secret');
    expect(service.currentUser()?.email).toBe('a@b.c');
    http.verify();
  });

  it('enrollMfa POSTs to /api/auth/mfa/enroll with an empty body', () => {
    const { service, http } = setup();

    service.enrollMfa().subscribe();
    const req = http.expectOne('/api/auth/mfa/enroll');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ data: { secret: 'BASE32SECRET', otpauthUri: 'otpauth://totp/Aurora:a@b.c' } });

    expect(service.getToken()).toBeNull(); // enrollment does not change the session
    http.verify();
  });

  it('confirmMfa POSTs {code} to /api/auth/mfa/confirm', () => {
    const { service, http } = setup();

    service.confirmMfa('123456').subscribe();
    const req = http.expectOne('/api/auth/mfa/confirm');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: '123456' });
    req.flush({ data: null });
    http.verify();
  });

  it('disableMfa POSTs {code} to /api/auth/mfa/disable', () => {
    const { service, http } = setup();

    service.disableMfa('123456').subscribe();
    const req = http.expectOne('/api/auth/mfa/disable');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: '123456' });
    req.flush({ data: null });
    http.verify();
  });

  it('mfaStatus GETs /api/auth/mfa/status', () => {
    const { service, http } = setup();

    let enabled: boolean | undefined;
    service.mfaStatus().subscribe((r) => {
      enabled = r.data.enabled;
    });
    const req = http.expectOne('/api/auth/mfa/status');
    expect(req.request.method).toBe('GET');
    req.flush({ data: { enabled: true } });

    expect(enabled).toBe(true);
    http.verify();
  });
});
