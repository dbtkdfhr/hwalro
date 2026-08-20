import { describe, expect, it } from 'vitest';
import { isVisibleHeatmapDensity } from './heatmapVisibility';

describe('isVisibleHeatmapDensity', () => {
  it('shows only finite densities of at least two people per square meter', () => {
    expect(isVisibleHeatmapDensity(1.99)).toBe(false);
    expect(isVisibleHeatmapDensity(2)).toBe(true);
    expect(isVisibleHeatmapDensity(4)).toBe(true);
    expect(isVisibleHeatmapDensity(Number.NaN)).toBe(false);
    expect(isVisibleHeatmapDensity(Number.POSITIVE_INFINITY)).toBe(false);
  });
});
