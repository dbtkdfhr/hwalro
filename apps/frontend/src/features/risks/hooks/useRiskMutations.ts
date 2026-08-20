import { useMutation, useQueryClient } from '@tanstack/react-query';
import { riskApi } from '../api/riskApi';
import type { RiskCreateRequest, RiskUpdateRequest } from '../types/risks';

export function useCreateRisk() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: RiskCreateRequest) => riskApi.create(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['risks'] }),
  });
}

export function useUpdateRisk() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: RiskUpdateRequest }) => riskApi.update(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['risks'] }),
  });
}

export function useDeleteRisk() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => riskApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['risks'] }),
  });
}
