import defaultDrawing from '../../../../../simulation-service/src/main/resources/drawings/default-drawing.json';
import type {
  AgentFrameBuffer,
  HeatmapFrame,
  SimulationDrawing,
  SimulationResultViewModel,
} from '../types';

const TOTAL_PEOPLE = 5000;
const DURATION_SECONDS = 264;
const AGENT_FRAME_STEP = 4;
const HEATMAP_FRAME_STEP = 2;
const INACTIVE = -10_000;

function mulberry32(seed: number) {
  return () => {
    let value = (seed += 0x6d2b79f5);
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4_294_967_296;
  };
}

function createAgentFrames(): AgentFrameBuffer[] {
  const random = mulberry32(20260806);
  const starts = new Float32Array(TOTAL_PEOPLE * 2);
  const exits = new Float32Array(TOTAL_PEOPLE * 2);
  const delays = new Float32Array(TOTAL_PEOPLE);
  const finishTimes = new Float32Array(TOTAL_PEOPLE);

  for (let index = 0; index < TOTAL_PEOPLE; index += 1) {
    const offset = index * 2;
    starts[offset] = 23 + random() * 126;
    starts[offset + 1] = 20 + random() * 62;
    const useSouthExit = random() > 0.42;
    exits[offset] = useSouthExit ? 151 : 17;
    exits[offset + 1] = useSouthExit ? 92 : 47;
    delays[index] = random() * 28;
    finishTimes[index] = 92 + random() * 172;
  }

  const frames: AgentFrameBuffer[] = [];
  for (let time = 0; time <= DURATION_SECONDS; time += AGENT_FRAME_STEP) {
    const positions = new Float32Array(TOTAL_PEOPLE * 2);
    let activeAgentCount = 0;
    for (let index = 0; index < TOTAL_PEOPLE; index += 1) {
      const offset = index * 2;
      if (time >= finishTimes[index]) {
        positions[offset] = INACTIVE;
        positions[offset + 1] = INACTIVE;
        continue;
      }
      activeAgentCount += 1;
      const travelDuration = Math.max(1, finishTimes[index] - delays[index]);
      const progress = Math.min(1, Math.max(0, (time - delays[index]) / travelDuration));
      const eased = progress * progress * (3 - 2 * progress);
      const curve = Math.sin(progress * Math.PI) * (index % 2 === 0 ? 5 : -5);
      positions[offset] = starts[offset] + (exits[offset] - starts[offset]) * eased;
      positions[offset + 1] =
        starts[offset + 1] + (exits[offset + 1] - starts[offset + 1]) * eased + curve;
    }
    frames.push({
      timeSeconds: time,
      positions,
      activeAgentCount,
      evacuatedCount: TOTAL_PEOPLE - activeAgentCount,
    });
  }
  return frames;
}

function createHeatmapFrames(columns: number, rows: number): HeatmapFrame[] {
  const frames: HeatmapFrame[] = [];
  for (let time = 0; time <= DURATION_SECONDS; time += HEATMAP_FRAME_STEP) {
    const values = new Float32Array(columns * rows);
    const pulse = Math.sin((time / DURATION_SECONDS) * Math.PI);
    const peaks = [
      { x: 0.59 + Math.sin(time / 30) * 0.035, y: 0.48, power: 4.8 * pulse },
      { x: 0.82, y: 0.74 + Math.cos(time / 38) * 0.04, power: 3.9 * pulse },
    ];
    for (let row = 0; row < rows; row += 1) {
      for (let column = 0; column < columns; column += 1) {
        const x = column / columns;
        const y = row / rows;
        let density = 0;
        for (const peak of peaks) {
          const dx = x - peak.x;
          const dy = y - peak.y;
          density += peak.power * Math.exp(-(dx * dx + dy * dy) / 0.008);
        }
        values[row * columns + column] = density;
      }
    }
    frames.push({ timeSeconds: time, values });
  }
  return frames;
}

export function createMockSimulationResult(simulationId: string): SimulationResultViewModel {
  const drawing: SimulationDrawing = {
    ...defaultDrawing,
    outsideBoundary: [
      { x: 0, y: 0 },
      { x: defaultDrawing.width, y: 0 },
      { x: defaultDrawing.width, y: defaultDrawing.height },
      { x: 0, y: defaultDrawing.height },
    ],
    pillars: [],
    fabrics: [],
    layoutTexts: [],
  };
  const agentFrames = createAgentFrames();
  const columns = 68;
  const rows = 40;
  return {
    simulationId,
    simulationResultId: Number(simulationId) || 1,
    title: '더현대 서울 B2 · 팝업 행사장',
    subtitle: '시뮬레이션 결과 분석',
    durationSeconds: DURATION_SECONDS,
    totalPeople: TOTAL_PEOPLE,
    maxDensity: 4.8,
    densityThreshold: 3.5,
    drawing,
    hazardZones: [
      { id: 1, centerX: 82, centerY: 44, radius: 8 },
      { id: 2, centerX: 128, centerY: 72, radius: 6 },
    ],
    agentFrames,
    heatmap: {
      originX: 0,
      originY: 0,
      columns,
      rows,
      cellWidth: drawing.width / columns,
      cellHeight: drawing.height / rows,
      maxDensity: 5,
      frames: createHeatmapFrames(columns, rows),
    },
    bottlenecks: [
      {
        id: 1,
        order: 1,
        name: '중앙 연결 통로',
        startTimeSeconds: 68,
        endTimeSeconds: 140,
        peakDensity: 4.8,
        thresholdValue: 3.5,
        geometry: { x: 93, y: 36, width: 31, height: 27 },
      },
      {
        id: 2,
        order: 2,
        name: '남측 출구 통로',
        startTimeSeconds: 148,
        endTimeSeconds: 206,
        peakDensity: 4.1,
        thresholdValue: 3.5,
        geometry: { x: 132, y: 66, width: 25, height: 24 },
      },
    ],
    evacuationProgress: agentFrames.map((frame) => ({
      timeSeconds: frame.timeSeconds,
      evacuatedCount: frame.evacuatedCount,
    })),
  };
}
