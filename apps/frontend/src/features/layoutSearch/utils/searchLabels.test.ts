import { describe, expect, it } from 'vitest';

import {
  findingLabel,
  operatorLabel,
  recommendationLabel,
} from './searchLabels';

describe('layout-search user-facing labels', () => {
  it('shows only the three official recommendation objectives when legacy geometry metadata is present', () => {
    const label = recommendationLabel(['TOTAL_TIME', 'AVERAGE_TIME', 'BALANCED', 'GEOMETRY']);

    expect(label).toBe('총시간 최적 · 평균시간 최적 · 균형 최적');
  });

  it('labels the configuration-space planner output instead of leaking raw enums', () => {
    expect(operatorLabel('CONFIGURATION_SPACE_SHAPE')).toBe('배치 조정');
    expect(findingLabel('IDEAL_FLOW')).toBe('이상 흐름');
  });

  it('does not surface legacy exit-imbalance proxy terms', () => {
    expect(operatorLabel('REBALANCE_EXIT')).toBe('배치 조정');
    expect(findingLabel('EXIT_IMBALANCE')).toBe('대피 흐름');
  });
});
