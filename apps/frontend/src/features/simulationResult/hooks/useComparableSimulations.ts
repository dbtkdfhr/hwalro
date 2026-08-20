import { useQuery } from '@tanstack/react-query';
import { simulationResultProvider } from '../api/simulationResultProvider';

export const COMPARABLE_SIMULATION_PAGE_SIZE = 5;

export function useComparableSimulations(simulationId: number, page: number, enabled: boolean) {
  return useQuery({
    queryKey: ['simulation-result', simulationId, 'comparable-simulations', page],
    queryFn: () =>
      simulationResultProvider.getComparableSimulations(
        simulationId,
        page,
        COMPARABLE_SIMULATION_PAGE_SIZE,
      ),
    enabled: enabled && Number.isSafeInteger(simulationId) && simulationId > 0,
  });
}
