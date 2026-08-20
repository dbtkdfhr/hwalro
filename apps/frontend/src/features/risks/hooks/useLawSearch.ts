import { useQuery } from '@tanstack/react-query';
import { lawApi } from '../api/lawApi';

const PAGE_SIZE = 20;

export function useLawSearch(query: string, page: number) {
  return useQuery({
    queryKey: ['law-search', query, page],
    queryFn: () => lawApi.searchRegulations(query, page, PAGE_SIZE),
    staleTime: 30_000,
  });
}
