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

/** 구역 배정 대상 직원. auth-service가 최소 정보만 노출한다. */
export interface EmployeeSummary {
  id: number;
  name: string;
}
