import { describe, expect, it } from 'vitest';
import type { SimulationExecutionStatus } from '../types';
import {
  getSimulationListAction,
  getSimulationListNavigationState,
  readStatusDialogSimulationId,
} from './simulationListAction';

describe('getSimulationListAction', () => {
  it.each([
    ['DRAFT', { type: 'navigate', to: '/simulations/7/setup' }],
    ['COMPLETED', { type: 'navigate', to: '/simulations/7/results' }],
    ['FAILED', { type: 'show-failure' }],
    ['CANCELLED', { type: 'show-cancelled' }],
    ['REQUESTED', { type: 'disabled' }],
    ['RUNNING', { type: 'disabled' }],
  ] satisfies Array<[SimulationExecutionStatus, ReturnType<typeof getSimulationListAction>]>)(
    '%s 상태의 목록 동작을 반환한다',
    (status, expected) => {
      expect(getSimulationListAction({ id: 7, status })).toEqual(expected);
    },
  );
});

describe('getSimulationListNavigationState', () => {
  it.each(['FAILED', 'CANCELLED'] satisfies SimulationExecutionStatus[])(
    '%s 상태는 목록에서 상태 모달을 열 ID를 전달한다',
    (status) => {
      expect(getSimulationListNavigationState({ id: 7, status })).toEqual({
        openStatusFor: 7,
      });
    },
  );

  it.each(['DRAFT', 'REQUESTED', 'RUNNING', 'COMPLETED'] satisfies SimulationExecutionStatus[])(
    '%s 상태는 상태 모달 이동 정보를 전달하지 않는다',
    (status) => {
      expect(getSimulationListNavigationState({ id: 7, status })).toBeNull();
    },
  );
});

describe('readStatusDialogSimulationId', () => {
  it('유효한 이동 상태에서 시뮬레이션 ID를 읽는다', () => {
    expect(readStatusDialogSimulationId({ openStatusFor: 7 })).toBe(7);
  });

  it.each([null, undefined, {}, { openStatusFor: 0 }, { openStatusFor: '7' }])(
    '유효하지 않은 이동 상태 %j는 무시한다',
    (state) => {
      expect(readStatusDialogSimulationId(state)).toBeNull();
    },
  );
});
