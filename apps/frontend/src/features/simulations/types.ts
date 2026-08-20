export interface SimulationPoint {
  x: number;
  y: number;
}

export interface SimulationLine {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface SimulationRect extends SimulationLine {
  id: number;
  rotation: number;
}

export interface SimulationExit extends SimulationLine {
  id: number;
}

export interface SimulationLayoutText {
  text: string;
  x: number;
  y: number;
}

export interface SimulationHazardZone {
  id?: number;
  centerX: number;
  centerY: number;
  radius: number;
}

export interface SimulationDrawing {
  layoutId: number;
  title: string;
  width: number;
  height: number;
  outsideBoundary: SimulationPoint[];
  walls: SimulationLine[];
  pillars: SimulationRect[];
  fabrics: SimulationRect[];
  layoutTexts: SimulationLayoutText[];
  exits: SimulationExit[];
}

export interface SimulationSetup {
  simulationId: number;
  layoutVersionId: number;
  parentSimulationId: number | null;
  title: string;
  status: string;
  createdAt: string;
  randomSeed: number;
  totalPeople: number;
  walkingSpeed: number;
  initialResponseTimeMean: number;
  initialResponseTimeStdDev: number;
  modelProfile: string;
  routingProfile: string;
  agentPositions: SimulationPoint[];
  hazardZones: SimulationHazardZone[];
  selectedExitIds: number[];
  drawing: SimulationDrawing;
}

export type SimulationExecutionStatus =
  'DRAFT' | 'REQUESTED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export type SimulationTerminationReason = 'ALL_EVACUATED' | 'MAX_DURATION' | 'STALLED';

export interface SimulationMetric {
  metricType: string;
  unit: string;
  metricValue: number;
}

export interface SimulationResultSummary {
  id: number;
  engineVersion: string;
  terminationReason: SimulationTerminationReason;
  simulationDurationSeconds: number;
  frameIntervalSeconds: number;
  timelineChunkCount: number;
  timelineChunkDurationSeconds: number;
  heatmapChunkCount: number;
  metrics: SimulationMetric[];
}

export interface SimulationExecution {
  simulationId: number;
  status: SimulationExecutionStatus;
  requestedAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  failureMessage: string | null;
  failureDetail?:
    | {
        code: 'AGENT_ROUTE_UNREACHABLE';
        agentId: number;
        currentPosition: SimulationPoint;
        recommendedPosition: SimulationPoint | null;
      }
    | {
        code: 'NO_REACHABLE_SELECTED_EXIT';
        affectedAgentCount: number;
        representativeAgentIds: number[];
        selectedExitIds: number[];
        reason: 'NO_EXIT_SEED_IN_OCCUPIED_COMPONENT';
      }
    | null;
  result: SimulationResultSummary | null;
}

export interface TimelineAgent {
  agentId: number;
  x: number;
  y: number;
}

export interface SimulationTimelineFrame {
  frameIndex: number;
  timeSeconds: number;
  activeAgentCount: number;
  evacuatedCount: number;
  agents: TimelineAgent[];
}

export interface SimulationTimelineChunk {
  schemaVersion: number;
  coordinateSystem: 'FLOOR_PLAN';
  coordinateUnit: 'METER';
  frameRate: number;
  chunkSequence: number;
  startFrame: number;
  endFrame: number;
  frames: SimulationTimelineFrame[];
  exitEvents: Array<{
    frameIndex: number;
    timeSeconds: number;
    agentId: number;
    exitId: number;
  }>;
}

export interface SimulationHeatmapChunk {
  schemaVersion: number;
  analysisVersion: 'GRID_COUNT_V1';
  coordinateSystem: 'FLOOR_PLAN';
  coordinateUnit: 'METER';
  densityMethod: 'GRID_COUNT';
  densityUnit: 'PERSON_PER_M2';
  frameRate: number;
  chunkSequence: number;
  startFrame: number;
  endFrame: number;
  grid: {
    originX: number;
    originY: number;
    cellSize: number;
    rows: number;
    columns: number;
    cellOrder: 'ROW_COLUMN_VALUE';
  };
  frames: Array<{
    frameIndex: number;
    timeSeconds: number;
    cells: Array<[row: number, column: number, density: number]>;
  }>;
}

export interface SimulationSummary {
  id: number;
  title?: string;
  status: string;
  createdAt: string;
  totalPeople: number;
}

export interface SimulationOverview {
  id: number;
  layoutVersionId: number;
  layoutId: number;
  layoutTitle: string;
  layoutVersionNumber: number;
  createdBy: number;
  title: string;
  status: SimulationExecutionStatus;
  createdAt: string;
  requestedAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  totalPeople: number;
  terminationReason: SimulationTerminationReason | null;
}

export interface SimulationOverviewPage {
  totalCount: number;
  page: number;
  size: number;
  hasNext: boolean;
  items: SimulationOverview[];
}

export interface CreateSimulationDraftRequest {
  layoutVersionId: number;
  parentSimulationId?: number;
  title?: string;
}

export interface UpdateSimulationSetupRequest {
  title?: string;
  walkingSpeed: number;
  initialResponseTimeMean: number;
  initialResponseTimeStdDev: number;
  agentPositions: SimulationPoint[];
  hazardZones: Array<Pick<SimulationHazardZone, 'centerX' | 'centerY' | 'radius'>>;
  selectedExitIds: number[];
}

export interface EditableHazardZone extends SimulationHazardZone {
  clientId: string;
}
