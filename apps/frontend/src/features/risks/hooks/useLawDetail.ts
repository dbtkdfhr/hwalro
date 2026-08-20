import { useQuery } from '@tanstack/react-query';
import { lawApi } from '../api/lawApi';

export function useLawDetail(serialNumber: string | undefined) {
  return useQuery({
    queryKey: ['law-detail', serialNumber],
    queryFn: () => lawApi.getRegulationDetail(serialNumber as string),
    enabled: Boolean(serialNumber),
    staleTime: 5 * 60 * 1000,
  });
}
