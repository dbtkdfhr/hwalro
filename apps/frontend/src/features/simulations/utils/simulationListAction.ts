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

export function readStatusDialogSimulationId(state: unknown): number | null {
  if (typeof state !== 'object' || state === null) return null;
  const simulationId = (state as Record<string, unknown>).openStatusFor;
  return typeof simulationId === 'number' && Number.isSafeInteger(simulationId) && simulationId > 0
    ? simulationId
    : null;
}
