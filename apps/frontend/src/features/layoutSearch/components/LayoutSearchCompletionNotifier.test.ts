import { describe, expect, it } from 'vitest';
import type { LayoutSearchMonitorItem, SearchStatus } from '../api/layoutSearchApi';
import { findNewlyCompletedLayoutSearches } from './LayoutSearchCompletionNotifier';

function monitorItem(searchId: number, status: SearchStatus): LayoutSearchMonitorItem {
  return {
    searchId,
    baselineSimulationId: 42,
    title: `테스트 시뮬레이션 ${searchId}`,
    status,
  };
}

describe('findNewlyCompletedLayoutSearches', () => {
  it('이번 조회에서 처음 완료 상태가 된 배치 개선안 탐색만 반환한다', () => {
    const previous = new Map<number, SearchStatus>([
      [1, 'GENERATING'],
      [2, 'COMPLETED'],
      [3, 'VERIFYING'],
      [4, 'GENERATING'],
    ]);
    const current = [
      monitorItem(1, 'COMPLETED'),
      monitorItem(2, 'COMPLETED'),
      monitorItem(3, 'NO_IMPROVEMENT'),
      monitorItem(4, 'FAILED'),
      monitorItem(5, 'COMPLETED'),
    ];

    expect(
      findNewlyCompletedLayoutSearches(previous, current).map(({ searchId }) => searchId),
    ).toEqual([1, 5]);
  });
});
