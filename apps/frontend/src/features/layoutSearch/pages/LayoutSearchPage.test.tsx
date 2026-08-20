// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AxiosError } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { simulationApi } from '../../simulations/api/simulationApi';
import type { SimulationDrawing, SimulationSetup } from '../../simulations/types';
import { layoutSearchApi, type LayoutSearch, type SearchCandidate } from '../api/layoutSearchApi';
import { HoldToCompare } from '../components/HoldToCompare';
import { SearchProgressHeader } from '../components/SearchProgressHeader';
import { POLL_INTERVAL_MS } from '../hooks/useLayoutSearch';
import LayoutSearchPage, { changedFabricIds } from './LayoutSearchPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function drawing(): SimulationDrawing {
  return {
    layoutId: 1,
    title: '테스트 도면',
    width: 10,
    height: 10,
    outsideBoundary: [
      { x: 0, y: 0 },
      { x: 10, y: 0 },
      { x: 10, y: 10 },
    ],
    walls: [],
    pillars: [],
    fabrics: [
      { id: 1, name: 'a', startX: 1, startY: 2, endX: 3, endY: 4, rotation: 0 },
      { id: 2, name: 'b', startX: 5, startY: 5, endX: 7, endY: 7, rotation: 0 },
    ],
    layoutTexts: [],
    exits: [{ id: 1, name: 'exit', startX: 10, startY: 4, endX: 10, endY: 6 }],
  };
}

function setup(): SimulationSetup {
  return {
    simulationId: 42,
    layoutVersionId: 7,
    parentSimulationId: null,
    title: '테스트 시뮬레이션',
    status: 'COMPLETED',
    createdAt: '2026-08-13T00:00:00Z',
    randomSeed: 1,
    totalPeople: 100,
    walkingSpeed: 1.2,
    initialResponseTimeMean: 0,
    initialResponseTimeStdDev: 0,
    modelProfile: 'DEFAULT',
    routingProfile: 'DEFAULT',
    agentPositions: [],
    hazardZones: [],
    selectedExitIds: [1],
    drawing: drawing(),
  };
}

function candidate(candidateId = 11): SearchCandidate {
  const operatorType = candidateId === 11 ? 'CLEAR_CORRIDOR' : 'RELIEVE_HOTSPOT';
  const isFirstCandidate = candidateId === 11;
  return {
    candidateId,
    round: 1,
    originFindingType: 'BOTTLENECK',
    operatorType,
    status: 'EVALUATED',
    rationale: {
      findingIndex: 0,
      operatorType,
      direction: 'NORMAL_POSITIVE',
      distanceMeters: 1,
      description: `${candidateId}번 개선안`,
    },
    changeSet: {
      schemaVersion: 1,
      coordinateUnit: 'METER',
      ops: [
        {
          type: 'MOVE_FABRIC',
          fabricId: isFirstCandidate ? 1 : 2,
          before: isFirstCandidate
            ? { startX: 1, startY: 2, endX: 3, endY: 4, rotation: 0 }
            : { startX: 5, startY: 5, endX: 7, endY: 7, rotation: 0 },
          after: isFirstCandidate
            ? { startX: 2, startY: 2, endX: 4, endY: 4, rotation: 0 }
            : { startX: 6, startY: 5, endX: 8, endY: 7, rotation: 0 },
        },
      ],
    },
    measuredMetrics: [
      { metricType: 'TOTAL_EVACUATION_TIME_SECONDS', unit: 'seconds', metricValue: 164.1 },
    ],
    delta: [
      {
        metricType: 'TOTAL_EVACUATION_TIME_SECONDS',
        baseline: 182.4,
        measured: 164.1,
        difference: -18.3,
        ratio: -0.1003,
      },
    ],
    rejectReason: null,
    totalMoveDistance: 1,
    preparedSimulation: null,
  };
}

function search(
  status: LayoutSearch['status'] = 'COMPLETED',
  improvedCandidates: SearchCandidate[] = [candidate(11), candidate(12)],
): LayoutSearch {
  return {
    searchId: 1,
    baselineSimulationId: 42,
    baselineLayoutVersionId: 7,
    status,
    plannerVersion: 'DIAGNOSTIC_BEAM_V1',
    progress: {
      verifiedCount: status === 'GENERATING' ? 0 : 2,
      plannedCount: status === 'GENERATING' ? null : 2,
      round: status === 'GENERATING' ? 0 : 1,
      baselineRunSeconds: 1840,
      estimatedRemainingSeconds: status === 'GENERATING' ? null : 0,
      trialCapSeconds: 210,
    },
    baselineMetrics: [],
    diagnosis:
      status === 'DIAGNOSING' || status === 'GENERATING'
        ? null
        : {
            findings: [
              {
                type: 'BOTTLENECK',
                severity: 0.82,
                region: null,
                evidence: null,
                description: '병목 구역을 확인했습니다.',
              },
            ],
          },
    improvedCandidates,
    rejectedCandidates: [],
    failureCode: null,
    failureMessage: null,
  };
}

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
  vi.restoreAllMocks();
  vi.useRealTimers();
});

async function renderPage() {
  await act(async () => {
    root.render(
      <MemoryRouter initialEntries={['/simulations/42/layout-search']}>
        <Routes>
          <Route path="/simulations/:simulationId/layout-search" element={<LayoutSearchPage />} />
        </Routes>
      </MemoryRouter>,
    );
    await Promise.resolve();
  });
  await act(async () => Promise.resolve());
}

function button(label: string) {
  const match = [...container.querySelectorAll('button')].find(
    (element) => element.textContent?.trim() === label,
  );
  if (!match) {
    throw new Error(`${label} 버튼을 찾을 수 없습니다.`);
  }
  return match;
}

describe('배치 비교 interaction', () => {
  it('개선 배치를 기본으로 표시하고 버튼과 키보드로 안전하게 전환한다', async () => {
    const before = drawing();
    const after = {
      ...before,
      fabrics: [{ ...before.fabrics[0], startX: 2 }, before.fabrics[1]],
    };
    await act(async () =>
      root.render(<HoldToCompare before={before} after={after} changedFabricIds={new Set([1])} />),
    );

    const beforeButton = button('기존 배치');
    const afterButton = button('개선 배치');
    expect(afterButton.getAttribute('aria-pressed')).toBe('true');
    expect(container.querySelector('.hold-compare__badge')?.textContent).toBe('개선 배치');

    await act(async () => beforeButton.click());
    expect(beforeButton.getAttribute('aria-pressed')).toBe('true');
    expect(container.querySelector('.hold-compare__badge')?.textContent).toBe('기존 배치');

    const viewport = container.querySelector<HTMLElement>('.hold-compare__viewport');
    await act(async () => {
      viewport?.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));
    });
    expect(container.querySelector('.hold-compare__badge')?.textContent).toBe('개선 배치');
    await act(async () => {
      viewport?.dispatchEvent(new KeyboardEvent('keyup', { key: ' ', bubbles: true }));
    });
    expect(container.querySelector('.hold-compare__badge')?.textContent).toBe('기존 배치');

    await act(async () => {
      viewport?.dispatchEvent(
        new PointerEvent('pointerdown', {
          bubbles: true,
          button: 0,
          isPrimary: true,
          pointerId: 1,
        }),
      );
    });
    expect(container.querySelector('.hold-compare__badge')?.textContent).toBe('개선 배치');
    await act(async () => {
      viewport?.dispatchEvent(
        new PointerEvent('pointerup', { bubbles: true, isPrimary: true, pointerId: 1 }),
      );
    });
    expect(container.querySelector('.hold-compare__badge')?.textContent).toBe('기존 배치');
  });

  it('stable id로 변경된 구조물을 찾는다', () => {
    const before = drawing();
    const after = {
      ...before,
      fabrics: [before.fabrics[1], { ...before.fabrics[0], startX: 2 }],
    };
    expect(changedFabricIds(before, after)).toEqual(new Set([1]));
  });
});

describe('배치 개선안 페이지 interaction', () => {
  it('준비 mutation 중복 클릭을 막고 후보별 준비 상태를 분리한다', async () => {
    let current = search();
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(setup());
    vi.spyOn(layoutSearchApi, 'latest').mockImplementation(async () => current);
    const preparation = vi
      .spyOn(layoutSearchApi, 'prepareSimulation')
      .mockImplementation(async (_searchId, candidateId) => {
        const preparedSimulation = { simulationId: 100 + candidateId, status: 'DRAFT' };
        current = {
          ...current,
          improvedCandidates: current.improvedCandidates.map((item) =>
            item.candidateId === candidateId ? { ...item, preparedSimulation } : item,
          ),
        };
        return preparedSimulation;
      });

    await renderPage();
    const prepare = button('이 개선안으로 시뮬레이션 준비');
    await act(async () => {
      prepare.click();
      prepare.click();
      await Promise.resolve();
    });

    expect(preparation).toHaveBeenCalledTimes(1);
    expect(container.textContent).toContain('시뮬레이션 준비됨');
    expect(container.querySelector('a[href="/simulations/111/setup"]')).not.toBeNull();

    const secondCandidate = [...container.querySelectorAll('button')].find((element) =>
      element.textContent?.includes('혼잡 완화'),
    );
    await act(async () => secondCandidate?.click());
    expect((button('이 개선안으로 시뮬레이션 준비') as HTMLButtonElement).disabled).toBe(false);
    expect(container.querySelector('a[href="/simulations/112/setup"]')).toBeNull();
  });

  it('한 후보가 준비 중이어도 다른 후보를 독립적으로 준비할 수 있다', async () => {
    const pending = new Map<number, (value: { simulationId: number; status: string }) => void>();
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(setup());
    vi.spyOn(layoutSearchApi, 'latest').mockResolvedValue(search());
    const preparation = vi
      .spyOn(layoutSearchApi, 'prepareSimulation')
      .mockImplementation(
        async (_searchId, candidateId) =>
          new Promise((resolve) => pending.set(candidateId, resolve)),
      );

    await renderPage();
    await act(async () => {
      button('이 개선안으로 시뮬레이션 준비').click();
      await Promise.resolve();
    });
    const secondCandidate = [...container.querySelectorAll('button')].find((element) =>
      element.textContent?.includes('혼잡 완화'),
    );
    await act(async () => secondCandidate?.click());

    expect((button('이 개선안으로 시뮬레이션 준비') as HTMLButtonElement).disabled).toBe(false);
    await act(async () => {
      button('이 개선안으로 시뮬레이션 준비').click();
      await Promise.resolve();
    });
    expect(preparation).toHaveBeenCalledTimes(2);

    await act(async () => {
      pending.get(11)?.({ simulationId: 111, status: 'DRAFT' });
      pending.get(12)?.({ simulationId: 112, status: 'DRAFT' });
      await Promise.resolve();
    });
  });

  it('terminal 상태에서 제약 설정 화면으로 돌아가 다시 탐색한다', async () => {
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(setup());
    vi.spyOn(layoutSearchApi, 'latest').mockResolvedValue(search('COMPLETED'));
    const start = vi
      .spyOn(layoutSearchApi, 'start')
      .mockResolvedValue({ searchId: 2, status: 'PENDING' });

    await renderPage();
    await act(async () => button('제약 설정 다시 열기').click());
    await act(async () => button('배치 개선안 탐색 시작').click());

    expect(start).toHaveBeenCalledWith(42, expect.anything(), false);
  });

  it('제약 설정 화면에서 배치 개선안 탐색을 시작한다', async () => {
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(setup());
    vi.spyOn(layoutSearchApi, 'latest').mockRejectedValue(
      new AxiosError('not found', undefined, undefined, undefined, { status: 404 } as never),
    );
    const start = vi
      .spyOn(layoutSearchApi, 'start')
      .mockResolvedValue({ searchId: 2, status: 'PENDING' });

    await renderPage();
    expect(container.textContent).toContain('구조물 제약 설정');
    await act(async () => {
      button('배치 개선안 탐색 시작').click();
      await Promise.resolve();
    });
    expect(start).toHaveBeenCalledWith(42, expect.anything(), false);
  });

  it('확인 옵션을 켜고 시작하면 실측 검증을 요청한다', async () => {
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(setup());
    vi.spyOn(layoutSearchApi, 'latest').mockRejectedValue(
      new AxiosError('not found', undefined, undefined, undefined, { status: 404 } as never),
    );
    const start = vi
      .spyOn(layoutSearchApi, 'start')
      .mockResolvedValue({ searchId: 2, status: 'PENDING' });

    await renderPage();
    const checkboxes = container.querySelectorAll('input[type="checkbox"]');
    const toggle = checkboxes[checkboxes.length - 1] as HTMLInputElement;
    await act(async () => {
      toggle.click();
      await Promise.resolve();
    });
    await act(async () => {
      button('배치 개선안 탐색 시작').click();
      await Promise.resolve();
    });

    expect(start).toHaveBeenCalledWith(42, expect.anything(), true);
  });

  it('원본 setup 오류에서 다시 시도하면 실제 setup을 재요청한다', async () => {
    vi.spyOn(simulationApi, 'getSetup')
      .mockRejectedValueOnce(new Error('setup error'))
      .mockResolvedValueOnce(setup());
    vi.spyOn(layoutSearchApi, 'latest').mockRejectedValue(
      new AxiosError('not found', undefined, undefined, undefined, { status: 404 } as never),
    );

    await renderPage();
    expect(button('다시 시도')).toBeTruthy();
    await act(async () => {
      button('다시 시도').click();
      await Promise.resolve();
    });

    expect(simulationApi.getSetup).toHaveBeenCalledTimes(2);
    expect(container.textContent).toContain('구조물 제약 설정');
  });

  it('active 상태에서만 poll하고 unmount 뒤 timer를 정리한다', async () => {
    vi.useFakeTimers();
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(setup());
    const latest = vi.spyOn(layoutSearchApi, 'latest').mockResolvedValue(search('GENERATING', []));

    await renderPage();
    expect(container.textContent).toContain('검증 중인 배치');
    expect(container.textContent).toContain('전체 후보 계산 중');
    await act(async () => vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS));
    expect(latest).toHaveBeenCalledTimes(2);

    await act(async () => root.unmount());
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2);
    expect(latest).toHaveBeenCalledTimes(2);
    root = createRoot(container);
  });
});

describe('종료 상태 렌더', () => {
  it('plannedCount 0과 실패 재탐색 문구를 렌더한다', async () => {
    const failed = search('FAILED');
    failed.progress.plannedCount = 0;
    await act(async () =>
      root.render(
        <SearchProgressHeader
          search={failed}
          onCancel={() => undefined}
          cancelling={false}
          onBackToResult={() => undefined}
          onRerun={() => undefined}
          rerunning={false}
        />,
      ),
    );
    expect(container.textContent).toContain('제약 설정 다시 열기');
  });
});
