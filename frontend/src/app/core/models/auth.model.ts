export interface AuthUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string | null;
  role: 'CUSTOMER' | 'ADMIN';
  // Optional: legacy localStorage sessions predate this field. The banner triggers
  // strictly on `=== false`, never on a falsy/undefined value.
  emailVerified?: boolean;
}

/**
 * The backend `AuthResponse`. For a normal sign-in this carries the real tokens
 * (status === 'AUTHENTICATED'). For an MFA-enrolled user the login response instead
 * carries `status === 'MFA_REQUIRED'` plus an opaque `mfaToken` and NO tokens — the
 * caller must complete the challenge via `POST /api/auth/mfa/verify` to get tokens.
 */
export interface AuthPayload {
  tokenType: 'Bearer';
  accessToken: string;
  refreshToken: string;
  expiresInMinutes: number;
  user: AuthUser;
  // Present on the login response. 'AUTHENTICATED' = tokens issued; 'MFA_REQUIRED' = challenge pending.
  status?: 'AUTHENTICATED' | 'MFA_REQUIRED';
  // Only present (non-null) when status === 'MFA_REQUIRED'; opaque, passed back to /mfa/verify.
  mfaToken?: string | null;
}

export interface MfaVerifyRequest {
  mfaToken: string;
  code: string;
}

/** Response of `POST /api/auth/mfa/enroll` — the shared secret to add to an authenticator app. */
export interface MfaEnrollResponse {
  secret: string;
  otpauthUri: string;
}

/** Response of `GET /api/auth/mfa/status`. */
export interface MfaStatusResponse {
  enabled: boolean;
}

export interface MfaCodeRequest {
  code: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface ResendVerificationRequest {
  email: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string;
}
