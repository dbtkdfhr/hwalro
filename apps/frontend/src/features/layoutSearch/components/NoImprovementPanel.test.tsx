// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach } from 'vitest';
import type { SearchCandidate } from '../api/layoutSearchApi';
import { describe, expect, it } from 'vitest';

import { CandidateTabs, getCandidateTabColorClass } from './NoImprovementPanel';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function candidate(candidateId: number, recommendationTypes: SearchCandidate['recommendationTypes']): SearchCandidate {
  return {
    candidateId,
    round: 1,
    originFindingType: 'IDEAL_ROUTE',
    operatorType: 'BOUNDARY_DOCKING',
    status: 'EVALUATED',
    recommendationTypes,
    rationale: null,
    changeSet: { schemaVersion: 1, coordinateUnit: 'METER', ops: [] },
    totalMoveDistance: null,
    measuredMetrics: [],
    delta: [],
    rejectReason: null,
    preparedSimulation: null,
  };
}

describe('CandidateTabs', () => {
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

  it('does not retain a special visual category for a legacy exit-imbalance operator', () => {
    expect(getCandidateTabColorClass('REBALANCE_EXIT')).toBe('is-improved');
  });

  it('shows the three official objectives before legacy geometry-only comparison candidates', async () => {
    await act(async () => {
      root.render(
        <CandidateTabs
          recommended={[
            candidate(10, ['GEOMETRY']),
            candidate(11, ['TOTAL_TIME']),
            candidate(12, ['AVERAGE_TIME']),
            candidate(13, ['BALANCED']),
          ]}
          comparisons={[]}
          activeKey="c-11"
          onSelect={() => undefined}
        />,
      );
    });

    const tabIds = [...container.querySelectorAll('[role="tab"]')].map((tab) => tab.id);
    expect(tabIds).toEqual([
      'candidate-tab-c-11',
      'candidate-tab-c-12',
      'candidate-tab-c-13',
      'candidate-tab-c-10',
    ]);
  });
});
