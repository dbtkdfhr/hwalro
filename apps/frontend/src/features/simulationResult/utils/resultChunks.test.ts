import { describe, expect, it } from 'vitest';
import type { SimulationHeatmapChunk, SimulationTimelineChunk } from '../../simulations/types';
import { convertPlaybackChunks } from './resultChunks';

describe('simulation result chunk conversion', () => {
  it('places sparse agents by id and keeps inactive slots offscreen', () => {
    const converted = convertPlaybackChunks(timelineChunk(), heatmapChunk(), 3, 4.8);

    expect(Array.from(converted.agentFrames[0].positions)).toEqual([
      -10_000, -10_000, 12, 7, -10_000, -10_000,
    ]);
    expect(converted.evacuationProgress).toEqual([{ timeSeconds: 4, evacuatedCount: 2 }]);
  });

  it('expands sparse heatmap cells and preserves the grid origin', () => {
    const converted = convertPlaybackChunks(timelineChunk(), heatmapChunk(), 3, 4.8);

    expect(converted.heatmap.originX).toBe(-5);
    expect(converted.heatmap.originY).toBe(2);
    const values = Array.from(converted.heatmap.frames[0].values);
    expect(values.slice(0, 3)).toEqual([0, 3.5, 0]);
    expect(values[3]).toBeCloseTo(4.8);
  });

  it('rejects mismatched chunk sequences', () => {
    const heatmap = heatmapChunk();
    heatmap.chunkSequence = 2;

    expect(() => convertPlaybackChunks(timelineChunk(), heatmap, 3, 4.8)).toThrow(
      '청크 순서가 일치하지 않습니다',
    );
  });
});

function timelineChunk(): SimulationTimelineChunk {
  return {
    schemaVersion: 1,
    coordinateSystem: 'FLOOR_PLAN',
    coordinateUnit: 'METER',
    frameRate: 1,
    chunkSequence: 1,
    startFrame: 4,
    endFrame: 4,
    frames: [
      {
        frameIndex: 4,
        timeSeconds: 4,
        activeAgentCount: 1,
        evacuatedCount: 2,
        agents: [{ agentId: 2, x: 12, y: 7 }],
      },
    ],
    exitEvents: [],
  };
}

function heatmapChunk(): SimulationHeatmapChunk {
  return {
    schemaVersion: 1,
    analysisVersion: 'GRID_COUNT_V1',
    coordinateSystem: 'FLOOR_PLAN',
    coordinateUnit: 'METER',
    densityMethod: 'GRID_COUNT',
    densityUnit: 'PERSON_PER_M2',
    frameRate: 1,
    chunkSequence: 1,
    startFrame: 4,
    endFrame: 4,
    grid: {
      originX: -5,
      originY: 2,
      cellSize: 1,
      rows: 2,
      columns: 2,
      cellOrder: 'ROW_COLUMN_VALUE',
    },
    frames: [
      {
        frameIndex: 4,
        timeSeconds: 4,
        cells: [
          [0, 1, 3.5],
          [1, 1, 4.8],
        ],
      },
    ],
  };
}
