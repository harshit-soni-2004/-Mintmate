export interface ApiError {
  code: string;
  message: string;
  details?: { field: string; message: string }[];
}

export interface ApiResponse<T> {
  data: T | null;
  error: ApiError | null;
  meta: unknown;
}

export interface User {
  id: string;
  email: string;
  displayName: string;
  timezone: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  user: User;
}
