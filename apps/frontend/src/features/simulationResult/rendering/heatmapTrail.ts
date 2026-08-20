import type { HeatmapFrame } from '../types';
import { isVisibleHeatmapDensity } from './heatmapVisibility';

export const HEATMAP_TRAIL_SECONDS = 5;
const OLDEST_FRAME_STRENGTH = 0.15;

export function composeHeatmapTrail(
  frames: HeatmapFrame[],
  timeSeconds: number,
  cellCount: number,
) {
  const result = new Float32Array(cellCount);
  for (const frame of frames) {
    const ageSeconds = timeSeconds - frame.timeSeconds;
    if (ageSeconds < 0 || ageSeconds > HEATMAP_TRAIL_SECONDS) continue;
    const recency = 1 - ageSeconds / HEATMAP_TRAIL_SECONDS;
    const strength = OLDEST_FRAME_STRENGTH + (1 - OLDEST_FRAME_STRENGTH) * recency;
    const valueCount = Math.min(cellCount, frame.values.length);
    for (let index = 0; index < valueCount; index += 1) {
      const density = frame.values[index];
      if (!isVisibleHeatmapDensity(density)) continue;
      result[index] = Math.max(result[index], density * strength);
    }
  }
  return result;
}
