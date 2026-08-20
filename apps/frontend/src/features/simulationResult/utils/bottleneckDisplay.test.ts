import { describe, expect, it } from 'vitest';
import type { DetectedBottleneck } from '../types';
import {
  BOTTLENECK_DISPLAY_BATCH_SIZE,
  getNextDisplayedBottleneckCount,
  rankBottlenecks,
  selectTopBottlenecks,
} from './bottleneckDisplay';

function bottleneck(
  id: number,
  peakDensity: number,
  startTimeSeconds: number,
  endTimeSeconds: number,
  order = id,
): DetectedBottleneck {
  return {
    id,
    order,
    name: `bottleneck-${id}`,
    startTimeSeconds,
    endTimeSeconds,
    peakDensity,
    thresholdValue: 3.5,
    geometry: { x: id, y: id, width: 1, height: 1 },
  };
}

describe('rankBottlenecks', () => {
  it('returns every bottleneck in risk order without mutating the input', () => {
    const source = [
      bottleneck(1, 4.1, 20, 30),
      bottleneck(2, 4.8, 30, 35),
      bottleneck(3, 4.8, 10, 20),
      bottleneck(4, 4.8, 5, 15),
      bottleneck(5, 4.5, 10, 30),
      bottleneck(6, 4.2, 0, 50),
    ];
    const originalIds = source.map((item) => item.id);

    expect(rankBottlenecks(source).map((item) => item.id)).toEqual([4, 3, 2, 5, 6, 1]);
    expect(source.map((item) => item.id)).toEqual(originalIds);
  });
});

describe('getNextDisplayedBottleneckCount', () => {
  it('adds five bottlenecks when a full next batch exists', () => {
    expect(getNextDisplayedBottleneckCount(BOTTLENECK_DISPLAY_BATCH_SIZE, 13)).toBe(10);
  });

  it('adds only the remaining bottlenecks in the final batch', () => {
    expect(getNextDisplayedBottleneckCount(10, 13)).toBe(13);
  });

  it('keeps the total when every bottleneck is already displayed', () => {
    expect(getNextDisplayedBottleneckCount(13, 13)).toBe(13);
  });
});

describe('selectTopBottlenecks', () => {
  it('returns only the five highest-ranked bottlenecks without mutating input', () => {
    const source = [
      bottleneck(1, 4.1, 20, 30),
      bottleneck(2, 4.8, 30, 35),
      bottleneck(3, 4.8, 10, 20),
      bottleneck(4, 4.8, 5, 15),
      bottleneck(5, 4.5, 10, 30),
      bottleneck(6, 4.2, 0, 50),
    ];
    const originalIds = source.map((item) => item.id);

    expect(selectTopBottlenecks(source).map((item) => item.id)).toEqual([4, 3, 2, 5, 6]);
    expect(source.map((item) => item.id)).toEqual(originalIds);
  });

  it('uses source order and then id for otherwise identical bottlenecks', () => {
    const source = [
      bottleneck(30, 5, 10, 20, 2),
      bottleneck(20, 5, 10, 20, 1),
      bottleneck(10, 5, 10, 20, 1),
    ];

    expect(selectTopBottlenecks(source).map((item) => item.id)).toEqual([10, 20, 30]);
  });
});
