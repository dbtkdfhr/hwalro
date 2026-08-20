import { describe, expect, it } from 'vitest';
import type { SimulationOverview } from '../types';
import { findNewlyCompletedSimulations } from './SimulationCompletionNotifier';

function simulation(
  id: number,
  createdBy: number,
  status: SimulationOverview['status'],
): SimulationOverview {
  return {
    id,
    createdBy,
    status,
    title: '테스트 시뮬레이션',
    layoutVersionId: 1,
    layoutId: 1,
    layoutTitle: '테스트 도면',
    layoutVersionNumber: 1,
    createdAt: '2026-08-10T12:00:00',
    requestedAt: null,
    startedAt: null,
    finishedAt: null,
    totalPeople: 10,
    terminationReason: null,
  };
}

describe('findNewlyCompletedSimulations', () => {
  it('현재 사용자의 상태가 완료로 바뀐 시뮬레이션만 반환한다', () => {
    const previous = new Map([
      [1, 'RUNNING'],
      [2, 'COMPLETED'],
      [3, 'RUNNING'],
      [4, 'RUNNING'],
    ] satisfies Array<[number, SimulationOverview['status']]>);
    const current = [
      simulation(1, 7, 'COMPLETED'),
      simulation(2, 7, 'COMPLETED'),
      simulation(3, 8, 'COMPLETED'),
      simulation(4, 7, 'CANCELLED'),
      simulation(5, 7, 'COMPLETED'),
    ];

    expect(findNewlyCompletedSimulations(previous, current, 7).map(({ id }) => id)).toEqual([1, 5]);
  });
});
