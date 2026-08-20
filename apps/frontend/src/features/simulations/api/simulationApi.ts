import { apiClient } from '../../../api/client';
import type {
  CreateSimulationDraftRequest,
  SimulationExecution,
  SimulationHeatmapChunk,
  SimulationOverview,
  SimulationOverviewPage,
  SimulationSetup,
  SimulationSummary,
  SimulationTimelineChunk,
  UpdateSimulationSetupRequest,
} from '../types';

export const simulationApi = {
  listMonitor: () =>
    apiClient
      .get<SimulationOverview[]>('/api/simulations/monitor')
      .then((response) => response.data),

  listOverview: (page: number, size: number, query?: string) =>
    apiClient
      .get<SimulationOverviewPage>('/api/simulations/overview', {
        params: { page, size, query: query?.trim() || undefined },
      })
      .then((response) => response.data),

  getOverview: (simulationId: number) =>
    apiClient
      .get<SimulationOverview>(`/api/simulations/${simulationId}/overview`)
      .then((response) => response.data),

  listByLayoutVersion: (layoutVersionId: number) =>
    apiClient
      .get<SimulationSummary[]>('/api/simulations', { params: { layoutVersionId } })
      .then((response) => response.data),

  createDraft: (body: CreateSimulationDraftRequest) =>
    apiClient
      .post<SimulationSetup>('/api/simulations/drafts', body)
      .then((response) => response.data),

  getSetup: (simulationId: number) =>
    apiClient
      .get<SimulationSetup>(`/api/simulations/${simulationId}/setup`)
      .then((response) => response.data),

  updateSetup: (simulationId: number, body: UpdateSimulationSetupRequest) =>
    apiClient
      .put<SimulationSetup>(`/api/simulations/${simulationId}/setup`, body)
      .then((response) => response.data),

  execute: (simulationId: number) =>
    apiClient
      .post<SimulationExecution>(`/api/simulations/${simulationId}/execute`)
      .then((response) => response.data),

  cancel: (simulationId: number) =>
    apiClient
      .post<SimulationExecution>(`/api/simulations/${simulationId}/cancel`)
      .then((response) => response.data),

  delete: (simulationId: number) =>
    apiClient.delete<void>(`/api/simulations/${simulationId}`).then((response) => response.data),

  getExecution: (simulationId: number) =>
    apiClient
      .get<SimulationExecution>(`/api/simulations/${simulationId}/execution`)
      .then((response) => response.data),

  getTimelineChunk: (simulationId: number, sequence: number) =>
    apiClient
      .get<SimulationTimelineChunk>(`/api/simulations/${simulationId}/timeline/${sequence}`)
      .then((response) => response.data),

  getHeatmapChunk: (simulationId: number, sequence: number) =>
    apiClient
      .get<SimulationHeatmapChunk>(`/api/simulations/${simulationId}/heatmap/${sequence}`)
      .then((response) => response.data),
};
