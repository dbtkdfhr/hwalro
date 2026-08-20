import type { SimulationHeatmapChunk, SimulationTimelineChunk } from '../../simulations/types';
import type { AgentFrameBuffer, SimulationPlaybackChunkData } from '../types';

const INACTIVE_POSITION = -10_000;

function toAgentFrame(
  frame: SimulationTimelineChunk['frames'][number],
  totalPeople: number,
): AgentFrameBuffer {
  const positions = new Float32Array(totalPeople * 2);
  positions.fill(INACTIVE_POSITION);
  for (const agent of frame.agents) {
    if (!Number.isInteger(agent.agentId) || agent.agentId < 1 || agent.agentId > totalPeople) {
      throw new Error(`유효하지 않은 에이전트 ID입니다: ${agent.agentId}`);
    }
    const offset = (agent.agentId - 1) * 2;
    positions[offset] = agent.x;
    positions[offset + 1] = agent.y;
  }
  return {
    timeSeconds: frame.timeSeconds,
    positions,
    activeAgentCount: frame.activeAgentCount,
    evacuatedCount: frame.evacuatedCount,
  };
}

export function convertPlaybackChunks(
  timeline: SimulationTimelineChunk,
  heatmap: SimulationHeatmapChunk,
  totalPeople: number,
  maxDensity: number,
): SimulationPlaybackChunkData {
  if (timeline.chunkSequence !== heatmap.chunkSequence) {
    throw new Error('타임라인과 히트맵 청크 순서가 일치하지 않습니다.');
  }
  const { grid } = heatmap;
  const heatmapFrames = heatmap.frames.map((frame) => {
    const values = new Float32Array(grid.rows * grid.columns);
    for (const [row, column, density] of frame.cells) {
      if (row < 0 || row >= grid.rows || column < 0 || column >= grid.columns) {
        throw new Error(`히트맵 셀 좌표가 격자 범위를 벗어났습니다: ${row}, ${column}`);
      }
      values[row * grid.columns + column] = density;
    }
    return { timeSeconds: frame.timeSeconds, values };
  });
  const agentFrames = timeline.frames.map((frame) => toAgentFrame(frame, totalPeople));

  return {
    sequence: timeline.chunkSequence,
    agentFrames,
    heatmap: {
      originX: grid.originX,
      originY: grid.originY,
      columns: grid.columns,
      rows: grid.rows,
      cellWidth: grid.cellSize,
      cellHeight: grid.cellSize,
      maxDensity,
      frames: heatmapFrames,
    },
    evacuationProgress: timeline.frames.map((frame) => ({
      timeSeconds: frame.timeSeconds,
      evacuatedCount: frame.evacuatedCount,
    })),
  };
}
