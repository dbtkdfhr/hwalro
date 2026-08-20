export interface AttachedLawRef {
  lawSerialNumber: string;
  lawArticleNumber: string;
}

export interface Risk {
  id: number;
  simulationResultId: number | null;
  assigneeId: number | null;
  assigneeName: string | null;
  title: string;
  description: string | null;
  startX: number | null;
  startY: number | null;
  endX: number | null;
  endY: number | null;
  severity: string;
  status: string;
  attachedLaws: AttachedLawRef[];
  createdAt: string;
  simulationTitle: string | null;
}

export interface RiskCreateRequest {
  simulationResultId: number | null;
  startX: number | null;
  startY: number | null;
  endX: number | null;
  endY: number | null;
  title: string;
  description: string | null;
  severity: string;
  status: string;
  attachedLaws: AttachedLawRef[];
}

export interface RiskUpdateRequest {
  title: string;
  description: string | null;
  severity: string;
  status: string;
  attachedLaws: AttachedLawRef[];
}

export interface RiskListResponse {
  totalCount: number;
  page: number;
  size: number;
  hasNext: boolean;
  items: Risk[];
}

export interface DrawingSegment {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation?: number;
}

export interface RiskDrawingContext {
  simulationResultId: number;
  simulationId: number;
  layoutTitle: string;
  title: string;
  drawing: {
    name: string;
    width: number;
    height: number;
    outsideBoundary: Array<{ x: number; y: number }>;
    walls: DrawingSegment[];
    exits: DrawingSegment[];
    pillars: DrawingSegment[];
    fabrics: DrawingSegment[];
    layoutTexts: Array<{ text: string; x: number; y: number }>;
  };
}
