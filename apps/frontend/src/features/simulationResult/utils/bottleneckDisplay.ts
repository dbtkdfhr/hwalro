import type { DetectedBottleneck } from '../types';

export const BOTTLENECK_DISPLAY_BATCH_SIZE = 5;

export function getNextDisplayedBottleneckCount(currentCount: number, totalCount: number): number {
  return Math.min(totalCount, currentCount + BOTTLENECK_DISPLAY_BATCH_SIZE);
}

function durationSeconds(bottleneck: DetectedBottleneck) {
  return bottleneck.endTimeSeconds - bottleneck.startTimeSeconds;
}

export function rankBottlenecks(bottlenecks: DetectedBottleneck[]): DetectedBottleneck[] {
  return [...bottlenecks].sort(
    (left, right) =>
      right.peakDensity - left.peakDensity ||
      durationSeconds(right) - durationSeconds(left) ||
      left.startTimeSeconds - right.startTimeSeconds ||
      left.order - right.order ||
      left.id - right.id,
  );
}

export function selectTopBottlenecks(
  bottlenecks: DetectedBottleneck[],
  limit = BOTTLENECK_DISPLAY_BATCH_SIZE,
): DetectedBottleneck[] {
  return rankBottlenecks(bottlenecks).slice(0, Math.max(0, limit));
}
