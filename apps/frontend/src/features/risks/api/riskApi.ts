import { apiClient } from '../../../api/client';
import type {
  Risk,
  RiskCreateRequest,
  RiskDrawingContext,
  RiskListResponse,
  RiskUpdateRequest,
} from '../types/risks';

export const riskApi = {
  list: (page: number, size: number, query?: string) =>
    apiClient
      .get<RiskListResponse>('/api/risks', {
        params: { page, size, query: query?.trim() || undefined },
      })
      .then((res) => res.data),
  listByLayout: (layoutId: number) =>
    apiClient.get<Risk[]>(`/api/risks/by-layout/${layoutId}`).then((res) => res.data),
  getDrawingForRisk: (riskId: number) =>
    apiClient.get<RiskDrawingContext>(`/api/risks/${riskId}/drawing`).then((res) => res.data),
  get: (id: number) => apiClient.get<Risk>(`/api/risks/${id}`).then((res) => res.data),
  create: (body: RiskCreateRequest) =>
    apiClient.post<Risk>('/api/risks', body).then((res) => res.data),
  update: (id: number, body: RiskUpdateRequest) =>
    apiClient.put<Risk>(`/api/risks/${id}`, body).then((res) => res.data),
  remove: (id: number) => apiClient.delete(`/api/risks/${id}`),
};
