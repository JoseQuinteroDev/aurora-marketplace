import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { AuthPayload, AuthUser, LoginRequest, RegisterRequest } from '../core/models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenKey = 'aurora_access_token';
  private readonly refreshKey = 'aurora_refresh_token';
  private readonly userKey = 'aurora_user';
  private readonly expiryKey = 'aurora_token_expires_at';
  private readonly userSignal = signal<AuthUser | null>(this.loadUser());

  readonly currentUser = this.userSignal.asReadonly();

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router
  ) {
    // Boot logged-out if a stale (expired) session is left in storage.
    if (this.getToken() && this.isSessionExpired()) {
      this.clearSession();
    }
  }

  /** True only when a token is present AND it has not passed its stored expiry. */
  isAuthenticated(): boolean {
    return Boolean(this.getToken()) && !this.isSessionExpired();
  }

  /** Reactive in templates (reads userSignal); also requires a live, non-expired session. */
  isAdmin(): boolean {
    return this.isAuthenticated() && this.userSignal()?.role === 'ADMIN';
  }

  login(request: LoginRequest) {
    return this.http.post<ApiResponse<AuthPayload>>('/api/auth/login', request).pipe(
      tap((response) => this.persistSession(response.data))
    );
  }

  register(request: RegisterRequest) {
    return this.http.post<ApiResponse<AuthPayload>>('/api/auth/register', request).pipe(
      tap((response) => this.persistSession(response.data))
    );
  }

  /**
   * Rotates the stored refresh token for a fresh access + refresh token pair.
   * The server single-uses the presented token, so we persist the rotated one in
   * place. Driven by the HTTP interceptor when an access token expires (15-min TTL).
   */
  refresh() {
    const refreshToken = this.getRefreshToken();
    return this.http.post<ApiResponse<AuthPayload>>('/api/auth/refresh', { refreshToken }).pipe(
      tap((response) => this.persistSession(response.data))
    );
  }

  /**
   * Clears the session. By default navigates home; pass `null` to clear without
   * navigating (used by the HTTP error interceptor, which redirects to /login itself).
   */
  logout(redirectTo: string | null = '/'): void {
    // On an explicit user logout, revoke the token server-side (best-effort, the
    // interceptor still attaches it). Skipped when redirectTo is null: that path is
    // the interceptor reacting to an already-rejected token, so there is nothing to
    // revoke. Never block the UI on the response.
    if (redirectTo && this.getToken()) {
      // Send the refresh token so the server revokes the whole family, not just
      // the current access token. Best-effort; never block the UI on the response.
      this.http.post('/api/auth/logout', { refreshToken: this.getRefreshToken() })
        .subscribe({ next: () => {}, error: () => {} });
    }

    this.clearSession();

    if (redirectTo) {
      this.router.navigateByUrl(redirectTo);
    }
  }

  private clearSession(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.refreshKey);
    localStorage.removeItem(this.userKey);
    localStorage.removeItem(this.expiryKey);
    this.userSignal.set(null);
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.refreshKey);
  }

  private persistSession(payload: AuthPayload): void {
    localStorage.setItem(this.tokenKey, payload.accessToken);
    localStorage.setItem(this.refreshKey, payload.refreshToken);
    localStorage.setItem(this.userKey, JSON.stringify(payload.user));
    localStorage.setItem(this.expiryKey, String(Date.now() + payload.expiresInMinutes * 60_000));
    this.userSignal.set(payload.user);
  }

  private isSessionExpired(): boolean {
    const raw = localStorage.getItem(this.expiryKey);

    if (!raw) {
      return false; // Legacy session without a stored expiry — treat as valid.
    }

    const expiresAt = Number(raw);
    return Number.isFinite(expiresAt) && Date.now() >= expiresAt;
  }

  private loadUser(): AuthUser | null {
    const rawUser = localStorage.getItem(this.userKey);

    if (!rawUser) {
      return null;
    }

    try {
      return JSON.parse(rawUser) as AuthUser;
    } catch {
      localStorage.removeItem(this.userKey);
      return null;
    }
  }
}
