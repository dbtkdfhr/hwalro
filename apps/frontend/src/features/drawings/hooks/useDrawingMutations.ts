import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/context/AuthContext';
import { drawingApi } from '../api/drawingApi';
import type { DrawingCreateRequest } from '../types/drawing';

export function useCreateDrawing() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id ?? 'unknown';
  return useMutation({
    mutationFn: (body: DrawingCreateRequest) => drawingApi.create(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['drawings', userId] }),
  });
}

export function useDeleteDrawing() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id ?? 'unknown';
  return useMutation({
    mutationFn: (id: number) => drawingApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['drawings', userId] }),
  });
}

export function useDuplicateDrawing() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id ?? 'unknown';
  return useMutation({
    mutationFn: (id: number) => drawingApi.duplicate(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['drawings', userId] }),
  });
}
