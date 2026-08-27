import type { SimulationExecutionStatus } from '../types';

type SimulationListItem = {
  id: number;
  status: SimulationExecutionStatus;
};

export type SimulationListAction =
  | { type: 'navigate'; to: string }
  | { type: 'show-failure' }
  | { type: 'show-cancelled' }
  | { type: 'disabled' };

export type SimulationListNavigationState = {
  openStatusFor: number;
};

export function getSimulationListAction({ id, status }: SimulationListItem): SimulationListAction {
  switch (status) {
    case 'DRAFT':
      return { type: 'navigate', to: `/simulations/${id}/setup` };
    case 'COMPLETED':
      return { type: 'navigate', to: `/simulations/${id}/results` };
    case 'FAILED':
      return { type: 'show-failure' };
    case 'CANCELLED':
      return { type: 'show-cancelled' };
    case 'REQUESTED':
    case 'RUNNING':
      return { type: 'disabled' };
  }
}

export function getSimulationListNavigationState({
  id,
  status,
}: SimulationListItem): SimulationListNavigationState | null {
  if (status !== 'FAILED' && status !== 'CANCELLED') return null;
  return { openStatusFor: id };
}

function readPositiveId(state: unknown, key: string): number | null {
  if (typeof state !== 'object' || state === null) return null;
  const value = (state as Record<string, unknown>)[key];
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? value : null;
}

export function readStatusDialogSimulationId(state: unknown): number | null {
  return readPositiveId(state, 'openStatusFor');
}

export function readLayoutSearchSimulationId(state: unknown): number | null {
  return readPositiveId(state, 'layoutSearchSimulationId');
}
