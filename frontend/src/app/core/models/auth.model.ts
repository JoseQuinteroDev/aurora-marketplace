export type NotificationChannel = 'EMAIL' | 'SMS';

export interface AuthUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string | null;
  notificationChannel: NotificationChannel;
  role: 'CUSTOMER' | 'ADMIN';
}

export interface NotificationPreferenceRequest {
  channel: NotificationChannel;
  phone?: string;
}

export interface AuthPayload {
  tokenType: 'Bearer';
  accessToken: string;
  expiresInMinutes: number;
  user: AuthUser;
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
