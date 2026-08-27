// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { simulationApi } from '../api/simulationApi';
import type { SimulationOverviewPage } from '../types';
import SimulationListPage from './SimulationListPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe('SimulationListPage', () => {
  let container: HTMLDivElement;
  let root: Root;
  let queryClient: QueryClient;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
  });

  afterEach(async () => {
    await act(async () => {
      root.unmount();
    });
    container.remove();
    vi.restoreAllMocks();
  });

  async function renderPage() {
    await act(async () => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/simulations']}>
            <Routes>
              <Route path="/simulations" element={<SimulationListPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>,
      );
    });
  }

  it('일반 시뮬레이션과 배치 개선안 시뮬레이션을 올바르게 구분하여 뱃지를 렌더링한다', async () => {
    const mockData: SimulationOverviewPage = {
      totalCount: 2,
      page: 1,
      size: 5,
      hasNext: false,
      items: [
        {
          id: 101,
          layoutVersionId: 1,
          layoutId: 1,
          layoutTitle: '본관 1층',
          layoutVersionNumber: 1,
          createdBy: 1,
          title: '일반 시뮬레이션',
          status: 'COMPLETED',
          createdAt: '2026-08-22T10:00:00Z',
          requestedAt: null,
          startedAt: null,
          finishedAt: null,
          totalPeople: 50,
          terminationReason: 'ALL_EVACUATED',
          isImprovement: false,
        },
        {
          id: 102,
          layoutVersionId: 2,
          layoutId: 1,
          layoutTitle: '본관 1층',
          layoutVersionNumber: 2,
          createdBy: 1,
          title: '개선안 시뮬레이션',
          status: 'DRAFT',
          createdAt: '2026-08-22T10:30:00Z',
          requestedAt: null,
          startedAt: null,
          finishedAt: null,
          totalPeople: 50,
          terminationReason: null,
          isImprovement: true,
        },
      ],
    };

    vi.spyOn(simulationApi, 'listOverview').mockResolvedValue(mockData);

    await renderPage();

    await vi.waitFor(() => {
      expect(container.textContent).toContain('일반 시뮬레이션');
    });
    expect(container.textContent).toContain('개선안 시뮬레이션');
    expect(container.textContent).toContain('배치 개선안');
  });
});
