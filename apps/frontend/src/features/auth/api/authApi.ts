import { apiClient } from '../../../api/client';
import type { AuthResponse, LoginRequest, User } from '../types/auth';

export const authApi = {
  login: (body: LoginRequest) =>
    apiClient.post<AuthResponse>('/api/auth/login', body).then((res) => res.data),
  refresh: () => apiClient.post<AuthResponse>('/api/auth/refresh').then((res) => res.data),
  logout: () => apiClient.post('/api/auth/logout').then(() => undefined),
  me: () => apiClient.get<User>('/api/auth/me').then((res) => res.data),
};
