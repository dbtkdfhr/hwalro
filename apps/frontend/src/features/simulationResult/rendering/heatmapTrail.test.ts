import { describe, expect, it } from 'vitest';
import type { HeatmapFrame } from '../types';
import { composeHeatmapTrail } from './heatmapTrail';

function frame(timeSeconds: number, values: number[]): HeatmapFrame {
  return { timeSeconds, values: Float32Array.from(values) };
}

describe('composeHeatmapTrail', () => {
  it('keeps the current qualifying density and excludes weak or future cells', () => {
    const result = composeHeatmapTrail([frame(10, [4, 1]), frame(11, [9, 9])], 10, 2);

    expect(Array.from(result)).toEqual([4, 0]);
  });

  it('keeps a low-strength trace at exactly five seconds', () => {
    const result = composeHeatmapTrail([frame(0, [2])], 5, 1);

    expect(result[0]).toBeCloseTo(0.3);
  });

  it('removes frames older than five seconds', () => {
    const result = composeHeatmapTrail([frame(0, [4])], 5.01, 1);

    expect(result[0]).toBe(0);
  });

  it('uses the strongest decayed value instead of accumulating frames', () => {
    const result = composeHeatmapTrail([frame(4, [4]), frame(5, [3])], 5, 1);

    expect(result[0]).toBeCloseTo(3.32);
  });
});
