export interface Point {
  x: number;
  y: number;
}

export interface Bounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface DrawingSegment {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface DrawingRect extends DrawingSegment {
  rotation?: number;
}

export interface DrawingText {
  text: string;
  x: number;
  y: number;
}

export interface SimulationDrawing {
  name: string;
  width: number;
  height: number;
  outsideBoundary: Point[];
  walls: DrawingSegment[];
  exits: DrawingSegment[];
  pillars: DrawingRect[];
  fabrics: DrawingRect[];
  layoutTexts: DrawingText[];
}

export interface AgentFrameBuffer {
  timeSeconds: number;
  positions: Float32Array;
  activeAgentCount: number;
  evacuatedCount: number;
}

export interface HeatmapFrame {
  timeSeconds: number;
  values: Float32Array;
}

export interface HeatmapData {
  originX: number;
  originY: number;
  columns: number;
  rows: number;
  cellWidth: number;
  cellHeight: number;
  maxDensity: number;
  frames: HeatmapFrame[];
}

export interface DetectedBottleneck {
  id: number;
  order: number;
  name: string;
  startTimeSeconds: number;
  endTimeSeconds: number;
  peakDensity: number;
  thresholdValue: number;
  geometry: Bounds;
}

export interface EvacuationPoint {
  timeSeconds: number;
  evacuatedCount: number;
}

export interface ComparableSimulation {
  id: number;
  simulationResultId: number;
  name: string;
  totalEvacuationTime: number;
}

export interface ComparableSimulationPage {
  totalCount: number;
  page: number;
  size: number;
  hasNext: boolean;
  items: ComparableSimulation[];
}

export interface RiskZone extends Bounds {
  id: string;
  name: string;
}

export interface HazardZone {
  id: number;
  centerX: number;
  centerY: number;
  radius: number;
}

export interface SimulationResultSummaryViewModel {
  simulationId: string;
  simulationResultId: number;
  title: string;
  subtitle: string;
  durationSeconds: number;
  totalPeople: number;
  maxDensity: number;
  densityThreshold: number;
  drawing: SimulationDrawing;
  hazardZones: HazardZone[];
  bottlenecks: DetectedBottleneck[];
}

export interface SimulationResultViewModel extends SimulationResultSummaryViewModel {
  agentFrames: AgentFrameBuffer[];
  heatmap: HeatmapData;
  evacuationProgress: EvacuationPoint[];
}

export interface SimulationPlaybackChunkData {
  sequence: number;
  agentFrames: AgentFrameBuffer[];
  heatmap: HeatmapData;
  evacuationProgress: EvacuationPoint[];
}

export interface SimulationResultProvider {
  getSummary(simulationId: string): Promise<SimulationResultSummaryViewModel | null>;
  getComparableSimulations(
    simulationId: number,
    page: number,
    size: number,
  ): Promise<ComparableSimulationPage>;
  getPlaybackChunk(
    simulationId: number,
    sequence: number,
    totalPeople: number,
    maxDensity: number,
  ): Promise<SimulationPlaybackChunkData>;
}
