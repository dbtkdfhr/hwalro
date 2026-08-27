import { apiClient } from '../../../api/client';
import type { AuthResponse, EmployeeSummary, LoginRequest, User } from '../types/auth';

export const authApi = {
  login: (body: LoginRequest) =>
    apiClient.post<AuthResponse>('/api/auth/login', body).then((res) => res.data),
  refresh: () => apiClient.post<AuthResponse>('/api/auth/refresh').then((res) => res.data),
  logout: () => apiClient.post('/api/auth/logout').then(() => undefined),
  me: () => apiClient.get<User>('/api/auth/me').then((res) => res.data),
  /** 구역 배정 대상 직원. 배정 권한이 있는 역할만 호출할 수 있다(없으면 403). */
  employees: () => apiClient.get<EmployeeSummary[]>('/api/auth/employees').then((res) => res.data),
};
