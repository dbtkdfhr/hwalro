import { describe, expect, it } from 'vitest';

import type { LayoutSearch } from '../api/layoutSearchApi';
import { layoutSearchReplies } from './searchFeed';

function search(status: LayoutSearch['status'], verifiedCount: number, plannedCount: number | null) {
  return {
    searchId: 9,
    baselineSimulationId: 4,
    baselineLayoutVersionId: 9,
    status,
    plannerVersion: 'IDEAL_ROUTE_DOCKING_V2',
    progress: {
      verifiedCount,
      plannedCount,
      round: 1,
      baselineRunSeconds: 205,
      estimatedRemainingSeconds: 900,
      trialCapSeconds: 236,
    },
    baselineMetrics: [],
    diagnosis: null,
    improvedCandidates: [],
    rejectedCandidates: [],
    failureCode: null,
    failureMessage: null,
  } satisfies LayoutSearch;
}

describe('layoutSearchReplies', () => {
  it.each([
    { verifiedCount: 1, plannedCount: 3 },
    { verifiedCount: 4, plannedCount: 7 },
  ])('검증 중 후보 배열이 비어도 실제 후보 수($plannedCount)를 안내한다', ({ verifiedCount, plannedCount }) => {
    const replies = layoutSearchReplies(search('VERIFYING', verifiedCount, plannedCount));

    expect(replies[0]).toMatchObject({
      tone: 'running',
      text: `개선안 후보 ${plannedCount}개를 찾았습니다 · ${verifiedCount}/${plannedCount} 검증 완료`,
    });
  });

  it('검증이 끝나 개선안이 없을 때만 후보 없음으로 안내한다', () => {
    const replies = layoutSearchReplies(search('NO_IMPROVEMENT', 3, 3));

    expect(replies[0]).toMatchObject({
      tone: 'done',
      text: '개선 가능한 후보를 찾지 못했습니다',
    });
  });
});
