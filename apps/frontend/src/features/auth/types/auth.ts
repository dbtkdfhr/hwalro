export interface User {
  id: number;
  loginId: string;
  name: string;
  roles: string[];
}

export interface LoginRequest {
  loginId: string;
  password: string;
  rememberMe: boolean;
}

export interface AuthResponse {
  accessToken: string;
  user: User;
}
