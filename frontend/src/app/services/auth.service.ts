import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { tap } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { AuthPayload, AuthUser, LoginRequest, RegisterRequest } from '../core/models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenKey = 'aurora_access_token';
  private readonly userKey = 'aurora_user';
  private readonly userSignal = signal<AuthUser | null>(this.loadUser());

  readonly currentUser = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => Boolean(this.getToken()));
  readonly isAdmin = computed(() => this.userSignal()?.role === 'ADMIN');

  constructor(private readonly http: HttpClient) {}

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

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.userKey);
    this.userSignal.set(null);
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  private persistSession(payload: AuthPayload): void {
    localStorage.setItem(this.tokenKey, payload.accessToken);
    localStorage.setItem(this.userKey, JSON.stringify(payload.user));
    this.userSignal.set(payload.user);
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
