// Mirrors backend DTOs (auth/dto/*.java). Keep in sync if those change.

export interface UserResponse {
  userId: string;
  email: string;
  fullName: string;
  tenantId: string;
  tenantName: string;
  tenantSlug: string;
  role: string;
}

/** Login/refresh response. refreshToken is always null — it's in an httpOnly cookie. */
export interface AuthResponse {
  accessToken: string;
  refreshToken: null;
  tokenType: string;
  expiresInSeconds: number;
  user: UserResponse;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  tenantName: string;
  tenantSlug: string;
}
