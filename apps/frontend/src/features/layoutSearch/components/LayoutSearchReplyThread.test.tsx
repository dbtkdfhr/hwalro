// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LayoutSearch, SearchCandidate } from '../api/layoutSearchApi';
import { LayoutSearchReplyThread } from './LayoutSearchReplyThread';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function search(status: LayoutSearch['status'], candidates: SearchCandidate[] = []): LayoutSearch {
  return {
    searchId: 12,
    baselineSimulationId: 2,
    baselineLayoutVersionId: 14,
    status,
    plannerVersion: 'IDEAL_ROUTE_DOCKING_V2',
    progress: {
      verifiedCount: 1,
      plannedCount: 6,
      round: 1,
      baselineRunSeconds: 100,
      estimatedRemainingSeconds: 500,
      trialCapSeconds: 130,
    },
    baselineMetrics: [],
    diagnosis: null,
    improvedCandidates: [],
    rejectedCandidates: candidates,
    failureCode: null,
    failureMessage: null,
  };
}

function failedCandidate(): SearchCandidate {
  return {
    candidateId: 68,
    round: 1,
    originFindingType: 'IDEAL_ROUTE',
    operatorType: 'BOUNDARY_DOCKING',
    status: 'FAILED',
    recommendationTypes: [],
    rationale: null,
    changeSet: { schemaVersion: 1, coordinateUnit: 'METER', ops: [] },
    totalMoveDistance: null,
    measuredMetrics: null,
    delta: [],
    rejectReason: 'AGENT_PLACEMENT_FAILED: placement failed',
    preparedSimulation: null,
  };
}

describe('LayoutSearchReplyThread', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('진행 중인 탐색을 목록에서 취소할 수 있다', async () => {
    const cancel = vi.fn();
    await act(async () => {
      root.render(
        <MemoryRouter>
          <LayoutSearchReplyThread search={search('VERIFYING')} onCancelSearch={cancel} />
        </MemoryRouter>,
      );
    });

    const button = [...container.querySelectorAll('button')].find((element) =>
      element.textContent?.includes('탐색 취소'),
    );
    expect(button).toBeDefined();
    await act(async () => button?.click());

    expect(cancel).toHaveBeenCalledWith(12);
  });

  it('초기 인원 배치 실패 원인을 사용자 문구로 표시한다', async () => {
    await act(async () => {
      root.render(
        <MemoryRouter>
          <LayoutSearchReplyThread search={search('FAILED', [failedCandidate()])} />
        </MemoryRouter>,
      );
    });

    expect(container.textContent).toContain(
      '변경 배치에서 초기 인원을 안전하게 배치할 공간이 부족합니다.',
    );
  });

  it('복수의 최적화 추천 타입을 각각 독립된 라벨 뱃지로 렌더링한다', async () => {
    const multiRecommendedCandidate: SearchCandidate = {
      candidateId: 101,
      round: 1,
      originFindingType: 'BOTTLENECK',
      operatorType: 'CLEAR_CORRIDOR',
      status: 'EVALUATED',
      recommendationTypes: ['TOTAL_TIME', 'AVERAGE_TIME', 'BALANCED'],
      rationale: null,
      changeSet: { schemaVersion: 1, coordinateUnit: 'METER', ops: [] },
      totalMoveDistance: 3.5,
      measuredMetrics: [],
      delta: [
        {
          metricType: 'TOTAL_EVACUATION_TIME_SECONDS',
          baseline: 120,
          measured: 100,
          difference: -20,
          ratio: -0.166,
        },
      ],
      rejectReason: null,
      preparedSimulation: null,
    };

    await act(async () => {
      root.render(
        <MemoryRouter>
          <LayoutSearchReplyThread
            search={{
              ...search('COMPLETED'),
              improvedCandidates: [multiRecommendedCandidate],
            }}
          />
        </MemoryRouter>,
      );
    });

    const labels = [...container.querySelectorAll('span')].map((el) => el.textContent?.trim());
    expect(labels).toContain('총시간 최적');
    expect(labels).toContain('평균시간 최적');
    expect(labels).toContain('균형 최적');
    // 기존처럼 ' · '로 한 덩어리로 붙어있지 않아야 함
    expect(labels).not.toContain('총시간 최적 · 평균시간 최적 · 균형 최적');
  });
});
