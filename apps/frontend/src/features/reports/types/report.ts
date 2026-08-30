export type ReportStatus = 'AI 작성 중' | '생성 실패' | '초안' | '작성 중' | '완료';

export type ReportListStatusFilter = '전체' | ReportStatus;

export interface ReportListItem {
  id: number;
  authorId: number;
  authorName: string | null;
  title: string;
  status: ReportStatus;
  updatedAt: string;
}

export interface ReportListResponse {
  totalCount: number;
  page: number;
  size: number;
  hasNext: boolean;
  items: ReportListItem[];
}

export interface ReportContent {
  overview: string;
  analysis: string;
  improvements: string;
}

export interface ReportDetailResponse {
  id: number;
  title: string;
  content: ReportContent;
  status: Exclude<ReportStatus, 'AI 작성 중' | '생성 실패' | '초안'>;
  createdAt: string;
  simulationResultIds: number[];
}

export interface ReportUpdateRequest {
  title: string;
  content: ReportContent;
  status: Exclude<ReportStatus, 'AI 작성 중' | '생성 실패' | '초안'>;
}

export interface AiReportDraftJobResponse {
  id: number;
  status: Extract<ReportStatus, 'AI 작성 중'>;
}

export interface AiReportDraftMonitorItem {
  id: number;
  title: string;
  status: ReportStatus;
}

export interface ReportVisualContext {
  simulationResultId: number;
  simulationId: number;
  layoutId: number;
  layoutVersionId: number;
  layoutTitle: string;
  drawing: import('../../simulationResult/types').SimulationDrawing;
  bottlenecks: import('../../simulationResult/types').DetectedBottleneck[];
  riskZones: ReportRiskZone[];
}

export interface ReportRiskZone {
  id: number;
  title: string;
  description: string | null;
  severity: string;
  geometry: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
}
