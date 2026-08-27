import { apiClient } from '../../../api/client';

export type SearchStatus =
  | 'PENDING'
  | 'DIAGNOSING'
  | 'GENERATING'
  | 'VERIFYING'
  | 'COMPLETED'
  | 'NO_IMPROVEMENT'
  | 'FAILED'
  | 'CANCELLED';

export type CandidateStatus =
  | 'GENERATED'
  | 'REJECTED_CONSTRAINT'
  | 'QUEUED'
  | 'RUNNING'
  | 'EVALUATED'
  | 'NOT_IMPROVED'
  | 'FAILED';

export interface SearchMetric {
  metricType: string;
  unit: string;
  metricValue: number;
}

export interface SearchRegion {
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface SearchEvidence {
  metric: string;
  value: number;
  unit: string;
  source: string;
}

export interface SearchFinding {
  type: string;
  severity: number;
  region: SearchRegion | null;
  evidence: SearchEvidence | null;
  description: string;
}

export interface SearchDiagnosis {
  findings: SearchFinding[];
}

export interface FabricTransform {
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
}

export interface ChangeOp {
  type: 'MOVE_FABRIC';
  fabricId: number;
  before: FabricTransform;
  after: FabricTransform;
}

export interface ChangeSet {
  schemaVersion: number;
  coordinateUnit: 'METER';
  ops: ChangeOp[];
}

export interface MetricDelta {
  metricType: string;
  baseline: number;
  measured: number;
  difference: number;
  ratio: number;
}

export interface CandidateRationale {
  findingIndex: number | null;
  operatorType: string | null;
  direction: string | null;
  distanceMeters: number | null;
  description: string | null;
}

export interface SearchProgress {
  verifiedCount: number;
  plannedCount: number | null;
  round: number;
  baselineRunSeconds: number;
  estimatedRemainingSeconds: number | null;
  trialCapSeconds: number;
}

export interface PreparedSimulation {
  simulationId: number;
  status: string;
}

export interface SearchCandidate {
  candidateId: number;
  round: number;
  originFindingType: string;
  operatorType: string;
  status: CandidateStatus;
  rationale: CandidateRationale | null;
  changeSet: ChangeSet;
  totalMoveDistance: number | null;
  measuredMetrics: SearchMetric[] | null;
  delta: MetricDelta[];
  rejectReason: string | null;
  preparedSimulation: PreparedSimulation | null;
}

export interface LayoutSearch {
  searchId: number;
  baselineSimulationId: number;
  baselineLayoutVersionId: number;
  status: SearchStatus;
  plannerVersion: string;
  progress: SearchProgress;
  baselineMetrics: SearchMetric[];
  diagnosis: SearchDiagnosis | null;
  improvedCandidates: SearchCandidate[];
  rejectedCandidates: SearchCandidate[];
  failureCode: string | null;
  failureMessage: string | null;
}

export interface StartSearchResult {
  searchId: number;
  status: string;
}

export interface LayoutSearchMonitorItem {
  searchId: number;
  baselineSimulationId: number;
  title: string;
  status: SearchStatus;
}

export const layoutSearchApi = {
  listMonitor: () =>
    apiClient
      .get<LayoutSearchMonitorItem[]>('/api/layout-searches/monitor')
      .then((response) => response.data),

  // 제약은 도면에 저장된 값을 서버가 읽는다. 실행마다 달라지는 값은 verify뿐이다.
  start: (simulationId: number, verify = false) =>
    apiClient
      .post<StartSearchResult>(`/api/simulations/${simulationId}/layout-searches`, { verify })
      .then((response) => response.data),

  latest: (simulationId: number) =>
    apiClient
      .get<LayoutSearch>(`/api/simulations/${simulationId}/layout-searches/latest`)
      .then((response) => response.data),

  get: (searchId: number) =>
    apiClient
      .get<LayoutSearch>(`/api/layout-searches/${searchId}`)
      .then((response) => response.data),

  cancel: (searchId: number) =>
    apiClient
      .post<StartSearchResult>(`/api/layout-searches/${searchId}/cancellation`)
      .then((response) => response.data),

  prepareSimulation: (searchId: number, candidateId: number) =>
    apiClient
      .post<PreparedSimulation>(
        `/api/layout-searches/${searchId}/candidates/${candidateId}/simulation-preparation`,
      )
      .then((response) => response.data),
};
