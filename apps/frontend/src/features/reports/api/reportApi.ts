import { apiClient } from '../../../api/client';
import type {
  AiReportDraftJobResponse,
  AiReportDraftMonitorItem,
  ReportDetailResponse,
  ReportListResponse,
  ReportStatus,
  ReportUpdateRequest,
  ReportVisualContext,
} from '../types/report';

interface ReportListParams {
  query?: string;
  status?: ReportStatus;
  page: number;
  size: number;
}

export interface AiReportDraftCreateRequest {
  sourceSimulationResultId: number;
  comparisonSimulationResultIds: number[];
}

export const reportApi = {
  listAiDraftMonitor: () =>
    apiClient
      .get<AiReportDraftMonitorItem[]>('/api/reports/ai-drafts/monitor')
      .then((response) => response.data),
  list: (params: ReportListParams) =>
    apiClient.get<ReportListResponse>('/api/reports', { params }).then((response) => response.data),
  get: (id: string) =>
    apiClient.get<ReportDetailResponse>(`/api/reports/${id}`).then((response) => response.data),
  getVisualContexts: (id: string) =>
    apiClient
      .get<ReportVisualContext[]>(`/api/reports/${id}/visual-contexts`)
      .then((response) => response.data),
  update: (id: string, request: ReportUpdateRequest) =>
    apiClient
      .put<ReportDetailResponse>(`/api/reports/${id}`, request)
      .then((response) => response.data),
  delete: (id: number) => apiClient.delete<void>(`/api/reports/${id}`),
  createAiDraft: (request: AiReportDraftCreateRequest) =>
    apiClient
      .post<AiReportDraftJobResponse>('/api/reports/ai-drafts', request)
      .then((response) => response.data),
  retryAiDraft: (id: number) =>
    apiClient
      .post<AiReportDraftJobResponse>(`/api/reports/${id}/ai-draft/retry`)
      .then((response) => response.data),
};
