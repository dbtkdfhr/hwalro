export interface DrawingWall {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface DrawingOutsideWall {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface DrawingExit {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface DrawingPillar {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
}

export interface DrawingFabric {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
}

export interface DrawingLayoutText {
  text: string;
  x: number;
  y: number;
}

export interface DrawingSummary {
  id: number;
  title: string;
  description: string | null;
  createdBy: number;
  createdAt: string;
  simulationCount: number;
}

export type DrawingLayoutVersionStatus = '초안' | '잠금';

export interface Drawing extends Omit<DrawingSummary, 'simulationCount'> {
  layoutVersionId: number;
  layoutVersionNumber: number;
  layoutVersionStatus: DrawingLayoutVersionStatus;
  width: number;
  height: number;
  walls: DrawingWall[];
  outsideWalls: DrawingOutsideWall[];
  pillars: DrawingPillar[];
  fabrics: DrawingFabric[];
  exits: DrawingExit[];
  layoutTexts: DrawingLayoutText[];
  version: number;
}

export interface DrawingCreateRequest {
  title: string | null;
  description: string | null;
  withDefaultData: boolean;
}

export interface DrawingUpdateRequest {
  title: string;
  description: string | null;
  walls: DrawingWall[];
  outsideWalls: DrawingOutsideWall[];
  pillars: DrawingPillar[];
  fabrics: DrawingFabric[];
  exits: DrawingExit[];
  layoutTexts: DrawingLayoutText[];
  expectedVersion: number;
}

export interface DrawingListResponse {
  totalCount: number;
  page: number;
  size: number;
  hasNext: boolean;
  items: DrawingSummary[];
}
