export interface Vec2 {
  x: number;
  y: number;
}

export interface Wall {
  id: string;
  /** 서버가 소유한 walls.id. 구역 멤버십이 이 값을 참조한다. 새로 그린 벽은 저장 전까지 null이다. */
  backendId: number | null;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  /** 도면 버전 전체를 아우르는 표시 순서. 클수록 위에 그려진다. 아직 저장되지 않은 요소는 undefined(맨 위)다. */
  displayOrder?: number;
}

export interface OutsideWall {
  id: string;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface Exit {
  id: string;
  /** 서버가 소유한 layout_exits.id. 새로 그린 비상구는 저장 전까지 null이다. */
  backendId: number | null;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface Pillar {
  id: string;
  /** 서버가 소유한 pillars.id. 구역 멤버십이 이 값을 참조한다. 새로 그린 기둥은 저장 전까지 null이다. */
  backendId: number | null;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
  /** 도면 버전 전체를 아우르는 표시 순서. 클수록 위에 그려진다. 아직 저장되지 않은 요소는 undefined(맨 위)다. */
  displayOrder?: number;
}

export interface Fabric {
  id: string;
  /** 서버가 소유한 fabrics.id. 구역 멤버십과 배치 제약이 이 값을 참조한다. */
  backendId: number | null;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
  /** 도면 버전 전체를 아우르는 표시 순서. 클수록 위에 그려진다. 아직 저장되지 않은 요소는 undefined(맨 위)다. */
  displayOrder?: number;
}

export interface LayoutText {
  id: string;
  text: string;
  x: number;
  y: number;
}

export interface DrawingDocument {
  name: string;
  width: number;
  height: number;
  walls: Wall[];
  outsideWalls: OutsideWall[];
  exits: Exit[];
  pillars: Pillar[];
  fabrics: Fabric[];
  layoutTexts: LayoutText[];
}

export type Tool =
  | 'select'
  | 'wall'
  | 'outsideWall'
  | 'exit'
  | 'text'
  | 'erase'
  | 'pillar'
  | 'fabric'
  /** 구역은 서버 소유 상태다. 문서(doc)에 들어가지 않고 그리기 draft만 편집기가 관리한다. */
  | 'zone';

export interface Camera {
  zoom: number;
  panX: number;
  panY: number;
}

export interface PointSelection {
  wallIds: string[];
  outsideWallIds: string[];
  exitIds: string[];
  textIds: string[];
  pillarIds: string[];
  fabricIds: string[];
}

export type WallHandle = 'start' | 'end';

export type RectHandle = 'start' | 'end';

export interface WallDraft {
  start: Vec2;
  end: Vec2;
  snappedToEndpoint: Vec2 | null;
  axisSnapped: boolean;
}

export interface RectDraft {
  start: Vec2;
  end: Vec2;
}

export interface TextDraft {
  point: Vec2;
  textId: string | null;
}

export type ValidationProblemKind = 'wall' | 'outsideWall' | 'exit' | 'pillar' | 'fabric';

export interface ValidationProblem {
  kind: ValidationProblemKind;
  name: string;
}

export type DragState =
  | {
      kind: 'move';
      origin: Vec2;
      originDoc: DrawingDocument;
    }
  | {
      kind: 'reshape';
      origin: Vec2;
      originDoc: DrawingDocument;
      elementKind: 'wall' | 'outsideWall' | 'pillar' | 'fabric';
      elementId: string;
      handle: RectHandle;
    }
  | {
      kind: 'rotate';
      origin: Vec2;
      originDoc: DrawingDocument;
      elementKind: 'pillar' | 'fabric';
      elementId: string;
    }
  | {
      kind: 'reshapeExit';
      origin: Vec2;
      originDoc: DrawingDocument;
      exitId: string;
      handle: WallHandle;
    }
  | {
      kind: 'erase';
      origin: Vec2;
      originDoc: DrawingDocument;
    };

export interface EditorState {
  doc: DrawingDocument;
  past: DrawingDocument[];
  future: DrawingDocument[];
  tool: Tool;
  selection: PointSelection;
  camera: Camera;
  draft: WallDraft | RectDraft | null;
  textDraft: TextDraft | null;
  drag: DragState | null;
  cursor: Vec2 | null;
  snapHint: Vec2 | null;
  validationProblems: ValidationProblem[];
  error: string | null;
  errorNonce: number;
  cameraFitNonce: number;
}

export interface SerializedWall {
  id: number | null;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  displayOrder?: number;
}

export interface SerializedOutsideWall {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface SerializedExit {
  id: number | null;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface SerializedPillar {
  id: number | null;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
  displayOrder?: number;
}

export interface SerializedFabric {
  id: number | null;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
  displayOrder?: number;
}

export interface SerializedText {
  text: string;
  x: number;
  y: number;
}

export interface SerializedDocument {
  name: string;
  width: number;
  height: number;
  walls: SerializedWall[];
  outsideWalls: SerializedOutsideWall[];
  exits: SerializedExit[];
  pillars: SerializedPillar[];
  fabrics: SerializedFabric[];
  layoutTexts: SerializedText[];
}
