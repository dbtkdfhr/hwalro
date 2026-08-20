import { apiClient } from '../../../api/client';
import type {
  Drawing,
  DrawingCreateRequest,
  DrawingListResponse,
  DrawingUpdateRequest,
} from '../types/drawing';

export const drawingApi = {
  list: (page: number, size: number, query?: string) =>
    apiClient
      .get<DrawingListResponse>('/api/drawings', {
        params: { page, size, query: query?.trim() || undefined },
      })
      .then((res) => res.data),
  get: (id: number) => apiClient.get<Drawing>(`/api/drawings/${id}`).then((res) => res.data),
  create: (body: DrawingCreateRequest) =>
    apiClient.post<Drawing>('/api/drawings', body).then((res) => res.data),
  duplicate: (id: number) =>
    apiClient.post<Drawing>(`/api/drawings/${id}/duplicate`).then((res) => res.data),
  update: (id: number, body: DrawingUpdateRequest) =>
    apiClient.put<Drawing>(`/api/drawings/${id}`, body).then((res) => res.data),
  remove: (id: number) => apiClient.delete(`/api/drawings/${id}`),
};
