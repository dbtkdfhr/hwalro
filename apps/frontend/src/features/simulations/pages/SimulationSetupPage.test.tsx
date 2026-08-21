// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { simulationApi } from '../api/simulationApi';
import type { SimulationExecution, SimulationSetup } from '../types';
import SimulationSetupPage from './SimulationSetupPage';

vi.mock('../components/SimulationCanvas', () => ({
  HAZARD_MAX_RADIUS: 50,
  HAZARD_MIN_RADIUS: 0.5,
  SimulationCanvas: () => <div data-testid="simulation-canvas" />,
}));
vi.mock('../../home/hooks/useRecordLastActivity', () => ({
  useRecordLastActivity: () => () => undefined,
}));

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function setup(): SimulationSetup {
  return {
    simulationId: 42,
    layoutVersionId: 7,
    parentSimulationId: null,
    title: '테스트 시뮬레이션',
    status: 'DRAFT',
    createdAt: '2026-08-20T00:00:00Z',
    randomSeed: 1,
    totalPeople: 1,
    walkingSpeed: 1.25,
    initialResponseTimeStdDev: 0,
    modelProfile: 'SFM_DEFAULT_V2',
    routingProfile: 'HAZARD_RADIAL_EXP_V3',
    agentPositions: [{ x: 1, y: 1 }],
    hazardZones: [],
    selectedExitIds: [501],
    drawing: {
      layoutId: 3,
      title: '테스트 도면',
      width: 10,
      height: 10,
      outsideBoundary: [
        { x: 0, y: 0 },
        { x: 10, y: 0 },
        { x: 10, y: 10 },
        { x: 0, y: 10 },
      ],
      walls: [],
      pillars: [],
      fabrics: [],
      layoutTexts: [],
      exits: [
        { id: 501, name: '출구 1', startX: 10, startY: 4, endX: 10, endY: 6 },
        { id: 502, name: '출구 2', startX: 0, startY: 4, endX: 0, endY: 6 },
      ],
    },
  };
}

function execution(): SimulationExecution {
  return {
    simulationId: 42,
    status: 'REQUESTED',
    requestedAt: '2026-08-20T00:00:01Z',
    startedAt: null,
    finishedAt: null,
    failureMessage: null,
    failureDetail: null,
    result: null,
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

async function renderPage(defaultAllExits = false) {
  await act(async () => {
    root.render(
      <MemoryRouter
        initialEntries={[
          defaultAllExits ? '/simulations/42/setup?defaultAllExits=true' : '/simulations/42/setup',
        ]}
      >
        <Routes>
          <Route path="/simulations/:simulationId/setup" element={<SimulationSetupPage />} />
          <Route path="/simulations" element={<div data-testid="simulation-list">목록</div>} />
        </Routes>
      </MemoryRouter>,
    );
    await Promise.resolve();
  });
  await act(async () => Promise.resolve());
}

function executeButton(): HTMLButtonElement {
  const match = [...container.querySelectorAll('button')].find(
    (element) => element.textContent?.trim() === '시뮬레이션 실행',
  );
  if (!(match instanceof HTMLButtonElement)) {
    throw new Error('시뮬레이션 실행 버튼을 찾을 수 없습니다.');
  }
  return match;
}

describe('출입구 기본 선택', () => {
  it('저장된 출입구 선택을 유지한다', async () => {
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(setup());

    await renderPage();

    const exitCheckboxes = [
      ...container.querySelectorAll<HTMLInputElement>(
        '.simulation-setup-exit-list input[type="checkbox"]',
      ),
    ];
    expect(exitCheckboxes).toHaveLength(2);
    expect(exitCheckboxes.map((checkbox) => checkbox.checked)).toEqual([true, false]);
  });

  it('새 초안 설정 페이지를 열면 모든 출입구를 선택한다', async () => {
    const current = setup();
    current.selectedExitIds = [];
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(current);

    await renderPage(true);

    const exitCheckboxes = [
      ...container.querySelectorAll<HTMLInputElement>(
        '.simulation-setup-exit-list input[type="checkbox"]',
      ),
    ];
    expect(exitCheckboxes).toHaveLength(2);
    expect(exitCheckboxes.every((checkbox) => checkbox.checked)).toBe(true);
  });

  it('저장된 빈 출입구 선택을 유지한다', async () => {
    const current = setup();
    current.selectedExitIds = [];
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(current);

    await renderPage();

    const exitCheckboxes = [
      ...container.querySelectorAll<HTMLInputElement>(
        '.simulation-setup-exit-list input[type="checkbox"]',
      ),
    ];
    expect(exitCheckboxes).toHaveLength(2);
    expect(exitCheckboxes.every((checkbox) => !checkbox.checked)).toBe(true);
  });
});

describe('실행 전 라우팅 검증', () => {
  it('저장과 검증에 성공하면 페이지에서 성공을 알리고 실행한 뒤 이동한다', async () => {
    const current = setup();
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(current);
    const update = vi.spyOn(simulationApi, 'updateSetup').mockResolvedValue(current);
    const validate = vi.spyOn(simulationApi, 'validateRouting').mockResolvedValue({
      valid: true,
      message: '경로 검증에 성공했습니다. 시뮬레이션 실행을 요청합니다.',
      failureDetail: null,
    });
    const execute = vi.spyOn(simulationApi, 'execute').mockResolvedValue(execution());

    await renderPage();
    vi.useFakeTimers();
    await act(async () => {
      executeButton().click();
      await vi.waitFor(() => expect(execute).toHaveBeenCalledWith(42));
    });
    expect(update).toHaveBeenCalledTimes(1);
    expect(update.mock.calls[0]?.[1]).not.toHaveProperty('initialResponseTimeMean');
    expect(validate).toHaveBeenCalledWith(42);
    expect(execute).toHaveBeenCalledWith(42);
    expect(update.mock.invocationCallOrder[0]).toBeLessThan(validate.mock.invocationCallOrder[0]);
    expect(validate.mock.invocationCallOrder[0]).toBeLessThan(execute.mock.invocationCallOrder[0]);
    expect(container.querySelector('[role="status"]')?.textContent).toContain('경로 검증에 성공');
    expect(container.querySelector('[data-testid="simulation-list"]')).toBeNull();

    await act(async () => vi.advanceTimersByTimeAsync(1000));
    expect(container.querySelector('[data-testid="simulation-list"]')).not.toBeNull();
  });

  it('검증에 실패하면 페이지에서 오류를 알리고 실행하거나 이동하지 않는다', async () => {
    const current = setup();
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(current);
    vi.spyOn(simulationApi, 'updateSetup').mockResolvedValue(current);
    vi.spyOn(simulationApi, 'validateRouting').mockResolvedValue({
      valid: false,
      message: '선택한 출입구에 도달할 수 없는 구역이 있습니다. 도면과 출입구를 확인해 주세요.',
      failureDetail: {
        code: 'NO_REACHABLE_SELECTED_EXIT',
        affectedAgentCount: 1,
        representativeAgentIds: [1],
        selectedExitIds: [501],
        reason: 'NO_EXIT_SEED_IN_OCCUPIED_COMPONENT',
      },
    });
    const execute = vi.spyOn(simulationApi, 'execute').mockResolvedValue(execution());

    await renderPage();
    await act(async () => {
      executeButton().click();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(container.querySelector('[role="alert"]')?.textContent).toContain(
      '선택한 출입구에 도달할 수 없는 구역',
    );
    expect(execute).not.toHaveBeenCalled();
    expect(container.querySelector('[data-testid="simulation-list"]')).toBeNull();
    expect(executeButton().disabled).toBe(false);
  });

  it('검증 통신 중에는 버튼을 비활성화하고 오류가 나면 페이지에 머문다', async () => {
    const current = setup();
    let rejectValidation: (reason?: unknown) => void = () => undefined;
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(current);
    vi.spyOn(simulationApi, 'updateSetup').mockResolvedValue(current);
    vi.spyOn(simulationApi, 'validateRouting').mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectValidation = reject;
        }),
    );
    const execute = vi.spyOn(simulationApi, 'execute').mockResolvedValue(execution());

    await renderPage();
    const button = executeButton();
    await act(async () => {
      button.click();
      await vi.waitFor(() => expect(button.disabled).toBe(true));
    });

    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('경로 검증 중');

    await act(async () => {
      rejectValidation(new Error('network error'));
      await Promise.resolve();
    });

    expect(container.querySelector('[role="alert"]')?.textContent).toContain(
      '요청 중 오류가 발생했습니다',
    );
    expect(execute).not.toHaveBeenCalled();
    expect(container.querySelector('[data-testid="simulation-list"]')).toBeNull();
    expect(button.disabled).toBe(false);
  });

  it('검증 성공 후 실행 요청이 실패하면 성공 알림을 오류 알림으로 교체한다', async () => {
    const current = setup();
    vi.spyOn(simulationApi, 'getSetup').mockResolvedValue(current);
    vi.spyOn(simulationApi, 'updateSetup').mockResolvedValue(current);
    vi.spyOn(simulationApi, 'validateRouting').mockResolvedValue({
      valid: true,
      message: '경로 검증에 성공했습니다. 시뮬레이션 실행을 요청합니다.',
      failureDetail: null,
    });
    vi.spyOn(simulationApi, 'execute').mockRejectedValue(new Error('network error'));

    await renderPage();
    await act(async () => {
      executeButton().click();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(container.querySelector('[role="status"]')).toBeNull();
    expect(container.querySelector('[role="alert"]')?.textContent).toContain(
      '요청 중 오류가 발생했습니다',
    );
    expect(container.querySelector('[data-testid="simulation-list"]')).toBeNull();
    expect(executeButton().disabled).toBe(false);
  });
});
