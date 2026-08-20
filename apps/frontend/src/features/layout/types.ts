export interface Vec2 {
  x: number;
  y: number;
}

export interface Wall {
  id: string;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
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
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface Pillar {
  id: string;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
}

export interface Fabric {
  id: string;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
}

export interface LayoutText {
  id: string;
  text: string;
  x: number;
  y: number;
}

export interface BackgroundImage {
  id: string;
  image: string;
  x: number;
  y: number;
  width: number;
  height: number;
  opacity: number;
  aspect: number;
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
  background: BackgroundImage | null;
}

export type Tool =
  | 'select'
  | 'wall'
  | 'outsideWall'
  | 'exit'
  | 'text'
  | 'erase'
  | 'background'
  | 'pillar'
  | 'fabric';

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
      kind: 'backgroundMove';
      origin: Vec2;
      originBg: BackgroundImage;
      originDoc: DrawingDocument;
    }
  | {
      kind: 'backgroundResize';
      origin: Vec2;
      originBg: BackgroundImage;
      originDoc: DrawingDocument;
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
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface SerializedOutsideWall {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface SerializedExit {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface SerializedPillar {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
}

export interface SerializedFabric {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
}

export interface SerializedText {
  text: string;
  x: number;
  y: number;
}

export interface SerializedBackground {
  image: string;
  x: number;
  y: number;
  width: number;
  height: number;
  opacity: number;
  aspect: number;
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
  background: SerializedBackground | null;
}
