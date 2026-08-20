import { apiClient } from '../../../api/client';
import type { SimulationOverview } from '../../simulations/types';
import type { LastActivity, LastActivityType, SimulationWorkSummary } from '../types/home';

export const homeApi = {
  /** 기록이 없으면 서버가 204를 반환하므로 본문이 비어 있을 수 있다. */
  getLastActivity: () =>
    apiClient
      .get<LastActivity | ''>('/api/auth/me/last-activity')
      .then((res) => (res.status === 204 || !res.data ? null : res.data)),

  recordLastActivity: (activityType: LastActivityType, resourceId: number) =>
    apiClient.put('/api/auth/me/last-activity', { activityType, resourceId }).then(() => undefined),

  getWorkSummary: () =>
    apiClient.get<SimulationWorkSummary>('/api/simulations/summary').then((res) => res.data),

  getSimulationOverview: (simulationId: number) =>
    apiClient
      .get<SimulationOverview>(`/api/simulations/${simulationId}/overview`)
      .then((res) => res.data),
};
