import type {
  Camera,
  DrawingDocument,
  EditorState,
  Exit,
  Fabric,
  OutsideWall,
  Pillar,
  RectHandle,
  Tool,
  ValidationProblem,
  Vec2,
  Wall,
  LayoutText,
} from '../types';
import { round1 } from '../utils/geometry';
import type { ElementHit } from '../utils/hitTest';
import { docSnapSources, snapPoint } from '../utils/snapping';
import { clampLineDraft, clampRectDraft, isInsideObstacleRect } from '../utils/collision';
import {
  createEmptyDocument,
  emptySelection,
  isInsideBounds,
  isRectInsideBounds,
  nextExitName,
  nextFabricName,
  nextOutsideWallName,
  nextPillarName,
  nextWallName,
  uid,
} from '../utils/document';
import { applyRedo, applyUndo, clearInteraction, commit } from './history';
import { applyDragUpdate } from './drag';
import { applySelectAt } from './selection';
import {
  applyBackgroundDragStart,
  applyBackgroundInsert,
  applyBackgroundOpacity,
  applyBackgroundRemove,
  applyBackgroundResize,
  applyBackgroundResizeStart,
} from './background';

export type EditorAction =
  | { type: 'setTool'; tool: Tool }
  | { type: 'setCamera'; camera: Camera }
  | { type: 'cursorMove'; world: Vec2 | null }
  | { type: 'wallStart'; point: Vec2 }
  | { type: 'wallUpdate'; point: Vec2 }
  | { type: 'wallCommit' }
  | { type: 'outsideWallStart'; point: Vec2 }
  | { type: 'outsideWallUpdate'; point: Vec2 }
  | { type: 'outsideWallCommit' }
  | { type: 'exitStart'; point: Vec2 }
  | { type: 'exitUpdate'; point: Vec2 }
  | { type: 'exitCommit' }
  | { type: 'pillarStart'; point: Vec2 }
  | { type: 'pillarUpdate'; point: Vec2 }
  | { type: 'pillarCommit' }
  | { type: 'fabricStart'; point: Vec2 }
  | { type: 'fabricUpdate'; point: Vec2 }
  | { type: 'fabricCommit' }
  | { type: 'textPlace'; point: Vec2 }
  | { type: 'textEditStart'; textId: string }
  | { type: 'textCommit'; text: string }
  | { type: 'textCancel' }
  | {
      type: 'selectAt';
      wallId: string | null;
      outsideWallId: string | null;
      exitId: string | null;
      textId: string | null;
      pillarId: string | null;
      fabricId: string | null;
      additive: boolean;
    }
  | { type: 'dragStartMove'; point: Vec2 }
  | {
      type: 'reshapeStart';
      elementKind: 'wall' | 'outsideWall' | 'pillar' | 'fabric';
      elementId: string;
      handle: RectHandle;
      point: Vec2;
    }
  | { type: 'reshapeExitStart'; exitId: string; handle: 'start' | 'end'; point: Vec2 }
  | {
      type: 'rotateStart';
      elementKind: 'pillar' | 'fabric';
      elementId: string;
      point: Vec2;
    }
  | { type: 'dragUpdate'; point: Vec2 }
  | { type: 'dragEnd' }
  | { type: 'eraseStart'; point: Vec2; hit: ElementHit }
  | { type: 'eraseUpdate'; hit: ElementHit }
  | { type: 'deleteSelection' }
  | { type: 'setValidationProblems'; problems: ValidationProblem[] }
  | { type: 'backgroundInsert'; image: string; aspect: number }
  | { type: 'backgroundDragStart'; point: Vec2 }
  | { type: 'backgroundResizeStart'; point: Vec2 }
  | { type: 'backgroundDragStart'; point: Vec2 }
  | { type: 'backgroundResize'; width: number }
  | { type: 'backgroundOpacity'; opacity: number }
  | { type: 'backgroundRemove' }
  | { type: 'undo' }
  | { type: 'redo' }
  | { type: 'commit'; prev: DrawingDocument; next: DrawingDocument }
  | { type: 'replaceDoc'; doc: DrawingDocument }
  | { type: 'loadDocument'; doc: DrawingDocument }
  | { type: 'renameDoc'; name: string }
  | { type: 'setError'; message: string | null }
  | { type: 'clearSelection' }
  | { type: 'escape' }
  | {
      type: 'updateWall';
      wallId: string;
      patch: Partial<Pick<Wall, 'startX' | 'startY' | 'endX' | 'endY'>>;
    }
  | {
      type: 'updateOutsideWall';
      wallId: string;
      patch: Partial<Pick<OutsideWall, 'startX' | 'startY' | 'endX' | 'endY'>>;
    }
  | {
      type: 'updateExit';
      exitId: string;
      patch: Partial<Pick<Exit, 'startX' | 'startY' | 'endX' | 'endY'>>;
    }
  | {
      type: 'updatePillar';
      pillarId: string;
      patch: Partial<Pick<Pillar, 'startX' | 'startY' | 'endX' | 'endY' | 'rotation'>>;
    }
  | {
      type: 'updateFabric';
      fabricId: string;
      patch: Partial<Pick<Fabric, 'startX' | 'startY' | 'endX' | 'endY' | 'rotation'>>;
    }
  | { type: 'updateText'; textId: string; patch: Partial<Pick<LayoutText, 'x' | 'y'>> };

export function createInitialState(): EditorState {
  return {
    doc: createEmptyDocument(),
    past: [],
    future: [],
    tool: 'select',
    selection: emptySelection(),
    camera: { zoom: 1, panX: 0, panY: 0 },
    draft: null,
    textDraft: null,
    drag: null,
    cursor: null,
    snapHint: null,
    validationProblems: [],
    error: null,
    errorNonce: 0,
    cameraFitNonce: 0,
  };
}

function applyDraftStart(state: EditorState, point: Vec2): EditorState {
  if (isInsideObstacleRect(point, state.doc)) {
    return {
      ...state,
      draft: null,
      snapHint: null,
      error: '기둥이나 구조물 안에는 배치할 수 없습니다.',
    };
  }
  const snapped = snapPoint(point, point, docSnapSources(state.doc), [], state.camera.zoom);
  return {
    ...state,
    draft: {
      start: snapped.point,
      end: snapped.point,
      axisSnapped: false,
      snappedToEndpoint: null,
    },
    snapHint: snapped.snappedToEndpoint,
    textDraft: null,
    error: null,
  };
}

function applyDraftUpdate(
  state: EditorState,
  point: Vec2,
  kind: 'wall' | 'outsideWall' | 'exit',
): EditorState {
  if (!state.draft) {
    return state;
  }
  const snapped = snapPoint(
    point,
    state.draft.start,
    docSnapSources(state.doc),
    [state.draft.start],
    state.camera.zoom,
  );
  const end =
    kind === 'exit' ? snapped.point : clampLineDraft(state.draft.start, snapped.point, state.doc);
  return {
    ...state,
    draft: {
      ...state.draft,
      end,
      axisSnapped: snapped.axisSnapped,
      snappedToEndpoint: snapped.snappedToEndpoint,
    },
  };
}

function applyRectDraftStart(state: EditorState, point: Vec2): EditorState {
  if (isInsideObstacleRect(point, state.doc)) {
    return {
      ...state,
      draft: null,
      snapHint: null,
      error: '기둥이나 구조물 안에는 배치할 수 없습니다.',
    };
  }
  const snapped = snapPoint(point, point, docSnapSources(state.doc), [], state.camera.zoom, false);
  return {
    ...state,
    draft: { start: snapped.point, end: snapped.point },
    snapHint: snapped.snappedToEndpoint,
    textDraft: null,
    error: null,
  };
}

function applyRectDraftUpdate(state: EditorState, point: Vec2): EditorState {
  if (!state.draft) {
    return state;
  }
  const snapped = snapPoint(
    point,
    state.draft.start,
    docSnapSources(state.doc),
    [state.draft.start],
    state.camera.zoom,
    false,
  );
  const end = clampRectDraft(state.draft.start, snapped.point, state.doc);
  return { ...state, draft: { ...state.draft, end } };
}

function applyEraseAt(state: EditorState, hit: ElementHit): EditorState {
  const { doc } = state;
  const walls = hit.wallId === null ? doc.walls : doc.walls.filter((w) => w.id !== hit.wallId);
  const outsideWalls =
    hit.outsideWallId === null
      ? doc.outsideWalls
      : doc.outsideWalls.filter((w) => w.id !== hit.outsideWallId);
  const exits = hit.exitId === null ? doc.exits : doc.exits.filter((e) => e.id !== hit.exitId);
  const layoutTexts =
    hit.textId === null ? doc.layoutTexts : doc.layoutTexts.filter((t) => t.id !== hit.textId);
  const pillars =
    hit.pillarId === null ? doc.pillars : doc.pillars.filter((p) => p.id !== hit.pillarId);
  const fabrics =
    hit.fabricId === null ? doc.fabrics : doc.fabrics.filter((f) => f.id !== hit.fabricId);
  if (
    walls === doc.walls &&
    outsideWalls === doc.outsideWalls &&
    exits === doc.exits &&
    layoutTexts === doc.layoutTexts &&
    pillars === doc.pillars &&
    fabrics === doc.fabrics
  ) {
    return state;
  }
  return {
    ...state,
    doc: { ...doc, walls, outsideWalls, exits, layoutTexts, pillars, fabrics },
  };
}

export function editorReducer(state: EditorState, action: EditorAction): EditorState {
  switch (action.type) {
    case 'setTool':
      return {
        ...state,
        tool: action.tool,
        draft: null,
        textDraft: null,
        snapHint: null,
        error: null,
      };

    case 'setCamera':
      return { ...state, camera: action.camera };

    case 'cursorMove':
      return { ...state, cursor: action.world };

    case 'wallStart':
      return applyDraftStart(state, action.point);

    case 'wallUpdate':
      return applyDraftUpdate(state, action.point, 'wall');

    case 'wallCommit': {
      if (!state.draft) {
        return state;
      }
      const { start, end } = state.draft;
      if (round1(end.x) === round1(start.x) && round1(end.y) === round1(start.y)) {
        return { ...state, draft: null, snapHint: null };
      }
      if (!isRectInsideBounds(state.doc, start.x, start.y, end.x, end.y)) {
        return { ...state, draft: null, snapHint: null };
      }
      const wall: Wall = {
        id: uid(),
        name: nextWallName(state.doc),
        startX: round1(start.x),
        startY: round1(start.y),
        endX: round1(end.x),
        endY: round1(end.y),
      };
      const next = { ...state.doc, walls: [...state.doc.walls, wall] };
      return commit(state, state.doc, next);
    }

    case 'outsideWallStart':
      return applyDraftStart(state, action.point);

    case 'outsideWallUpdate':
      return applyDraftUpdate(state, action.point, 'outsideWall');

    case 'outsideWallCommit': {
      if (!state.draft) {
        return state;
      }
      const { start, end } = state.draft;
      if (round1(end.x) === round1(start.x) && round1(end.y) === round1(start.y)) {
        return { ...state, draft: null, snapHint: null };
      }
      if (!isRectInsideBounds(state.doc, start.x, start.y, end.x, end.y)) {
        return { ...state, draft: null, snapHint: null };
      }
      const wall: OutsideWall = {
        id: uid(),
        name: nextOutsideWallName(state.doc),
        startX: round1(start.x),
        startY: round1(start.y),
        endX: round1(end.x),
        endY: round1(end.y),
      };
      const next = { ...state.doc, outsideWalls: [...state.doc.outsideWalls, wall] };
      return commit(state, state.doc, next);
    }

    case 'exitStart':
      return applyDraftStart(state, action.point);

    case 'exitUpdate':
      return applyDraftUpdate(state, action.point, 'exit');

    case 'exitCommit': {
      if (!state.draft) {
        return state;
      }
      const { start, end } = state.draft;
      if (round1(end.x) === round1(start.x) && round1(end.y) === round1(start.y)) {
        return { ...state, draft: null, snapHint: null };
      }
      if (!isRectInsideBounds(state.doc, start.x, start.y, end.x, end.y)) {
        return { ...state, draft: null, snapHint: null };
      }
      const exit: Exit = {
        id: uid(),
        name: nextExitName(state.doc),
        startX: round1(start.x),
        startY: round1(start.y),
        endX: round1(end.x),
        endY: round1(end.y),
      };
      const next = { ...state.doc, exits: [...state.doc.exits, exit] };
      return commit(state, state.doc, next);
    }

    case 'pillarStart':
      return applyRectDraftStart(state, action.point);

    case 'pillarUpdate':
      return applyRectDraftUpdate(state, action.point);

    case 'pillarCommit': {
      if (!state.draft) {
        return state;
      }
      const { start, end } = state.draft;
      if (round1(end.x) === round1(start.x) && round1(end.y) === round1(start.y)) {
        return { ...state, draft: null, snapHint: null };
      }
      if (!isRectInsideBounds(state.doc, start.x, start.y, end.x, end.y)) {
        return { ...state, draft: null, snapHint: null };
      }
      const pillar: Pillar = {
        id: uid(),
        name: nextPillarName(state.doc),
        startX: round1(start.x),
        startY: round1(start.y),
        endX: round1(end.x),
        endY: round1(end.y),
        rotation: 0,
      };
      const next = { ...state.doc, pillars: [...state.doc.pillars, pillar] };
      return commit(state, state.doc, next);
    }

    case 'fabricStart':
      return applyRectDraftStart(state, action.point);

    case 'fabricUpdate':
      return applyRectDraftUpdate(state, action.point);

    case 'fabricCommit': {
      if (!state.draft) {
        return state;
      }
      const { start, end } = state.draft;
      if (round1(end.x) === round1(start.x) && round1(end.y) === round1(start.y)) {
        return { ...state, draft: null, snapHint: null };
      }
      if (!isRectInsideBounds(state.doc, start.x, start.y, end.x, end.y)) {
        return { ...state, draft: null, snapHint: null };
      }
      const fabric: Fabric = {
        id: uid(),
        name: nextFabricName(state.doc),
        startX: round1(start.x),
        startY: round1(start.y),
        endX: round1(end.x),
        endY: round1(end.y),
        rotation: 0,
      };
      const next = { ...state.doc, fabrics: [...state.doc.fabrics, fabric] };
      return commit(state, state.doc, next);
    }

    case 'textPlace':
      return {
        ...state,
        textDraft: { point: action.point, textId: null },
        draft: null,
        snapHint: null,
        error: null,
      };

    case 'textEditStart': {
      const existing = state.doc.layoutTexts.find((t) => t.id === action.textId);
      if (!existing) {
        return state;
      }
      return {
        ...state,
        textDraft: { point: { x: existing.x, y: existing.y }, textId: existing.id },
        draft: null,
        snapHint: null,
        error: null,
      };
    }

    case 'textCommit': {
      if (!state.textDraft) {
        return state;
      }
      const textId = state.textDraft.textId;
      if (textId === null) {
        if (action.text.trim() === '') {
          return { ...state, textDraft: null };
        }
        if (!isInsideBounds(state.doc, state.textDraft.point.x, state.textDraft.point.y)) {
          return { ...state, textDraft: null };
        }
        const text: LayoutText = {
          id: uid(),
          text: action.text,
          x: round1(state.textDraft.point.x),
          y: round1(state.textDraft.point.y),
        };
        const next = { ...state.doc, layoutTexts: [...state.doc.layoutTexts, text] };
        return commit(state, state.doc, next);
      }
      const existing = state.doc.layoutTexts.find((t) => t.id === textId);
      if (!existing) {
        return { ...state, textDraft: null };
      }
      if (action.text.trim() === '' || action.text === existing.text) {
        return { ...state, textDraft: null };
      }
      const nextText: LayoutText = { ...existing, text: action.text };
      const next = {
        ...state.doc,
        layoutTexts: state.doc.layoutTexts.map((t) => (t.id === textId ? nextText : t)),
      };
      return commit(state, state.doc, next);
    }

    case 'textCancel':
      return { ...state, textDraft: null };

    case 'selectAt':
      return applySelectAt(state, action);

    case 'dragStartMove':
      return { ...state, drag: { kind: 'move', origin: action.point, originDoc: state.doc } };

    case 'reshapeStart':
      return {
        ...state,
        drag: {
          kind: 'reshape',
          origin: action.point,
          originDoc: state.doc,
          elementKind: action.elementKind,
          elementId: action.elementId,
          handle: action.handle,
        },
      };

    case 'reshapeExitStart':
      return {
        ...state,
        drag: {
          kind: 'reshapeExit',
          origin: action.point,
          originDoc: state.doc,
          exitId: action.exitId,
          handle: action.handle,
        },
      };

    case 'rotateStart':
      return {
        ...state,
        drag: {
          kind: 'rotate',
          origin: action.point,
          originDoc: state.doc,
          elementKind: action.elementKind,
          elementId: action.elementId,
        },
      };

    case 'dragUpdate':
      return applyDragUpdate(state, action.point);

    case 'backgroundInsert':
      return applyBackgroundInsert(state, action.image, action.aspect);

    case 'backgroundDragStart':
      return applyBackgroundDragStart(state, action.point);

    case 'backgroundResizeStart':
      return applyBackgroundResizeStart(state, action.point);

    case 'backgroundResize':
      return applyBackgroundResize(state, action.width);

    case 'backgroundOpacity':
      return applyBackgroundOpacity(state, action.opacity);

    case 'backgroundRemove':
      return applyBackgroundRemove(state);

    case 'dragEnd': {
      if (!state.drag) {
        return state;
      }
      if (state.drag.originDoc === state.doc) {
        return { ...state, drag: null };
      }
      return commit(state, state.drag.originDoc, state.doc);
    }

    case 'eraseStart': {
      const next = applyEraseAt(state, action.hit);
      return {
        ...next,
        drag: { kind: 'erase', origin: action.point, originDoc: state.doc },
        error: null,
      };
    }

    case 'eraseUpdate':
      return applyEraseAt(state, action.hit);

    case 'deleteSelection': {
      const { selection } = state;
      if (
        selection.wallIds.length === 0 &&
        selection.outsideWallIds.length === 0 &&
        selection.exitIds.length === 0 &&
        selection.textIds.length === 0 &&
        selection.pillarIds.length === 0 &&
        selection.fabricIds.length === 0
      ) {
        return state;
      }
      const walls = state.doc.walls.filter((w) => !selection.wallIds.includes(w.id));
      const outsideWalls = state.doc.outsideWalls.filter(
        (w) => !selection.outsideWallIds.includes(w.id),
      );
      const exits = state.doc.exits.filter((e) => !selection.exitIds.includes(e.id));
      const layoutTexts = state.doc.layoutTexts.filter((t) => !selection.textIds.includes(t.id));
      const pillars = state.doc.pillars.filter((p) => !selection.pillarIds.includes(p.id));
      const fabrics = state.doc.fabrics.filter((f) => !selection.fabricIds.includes(f.id));
      const next = { ...state.doc, walls, outsideWalls, exits, layoutTexts, pillars, fabrics };
      return commit(state, state.doc, next);
    }

    case 'commit':
      return commit(state, action.prev, action.next);

    case 'replaceDoc':
      return { ...state, doc: action.doc, error: null, validationProblems: [] };

    case 'renameDoc': {
      const next = { ...state.doc, name: action.name };
      return commit(state, state.doc, next);
    }

    case 'loadDocument':
      return {
        ...clearInteraction({ ...state, doc: action.doc }),
        past: [],
        future: [],
        validationProblems: [],
        cameraFitNonce: state.cameraFitNonce + 1,
      };

    case 'updateWall': {
      const wall = state.doc.walls.find((w) => w.id === action.wallId);
      if (!wall) {
        return state;
      }
      const nextWall: Wall = { ...wall, ...action.patch };
      if (
        round1(nextWall.startX) === round1(nextWall.endX) &&
        round1(nextWall.startY) === round1(nextWall.endY)
      ) {
        return state;
      }
      if (
        !isRectInsideBounds(
          state.doc,
          nextWall.startX,
          nextWall.startY,
          nextWall.endX,
          nextWall.endY,
        )
      ) {
        return state;
      }
      const next = {
        ...state.doc,
        walls: state.doc.walls.map((w) => (w.id === wall.id ? nextWall : w)),
      };
      return commit(state, state.doc, next);
    }

    case 'updateExit': {
      const exit = state.doc.exits.find((e) => e.id === action.exitId);
      if (!exit) {
        return state;
      }
      const nextExit: Exit = { ...exit, ...action.patch };
      if (
        round1(nextExit.startX) === round1(nextExit.endX) &&
        round1(nextExit.startY) === round1(nextExit.endY)
      ) {
        return state;
      }
      if (
        !isRectInsideBounds(
          state.doc,
          nextExit.startX,
          nextExit.startY,
          nextExit.endX,
          nextExit.endY,
        )
      ) {
        return state;
      }
      const next = {
        ...state.doc,
        exits: state.doc.exits.map((e) => (e.id === exit.id ? nextExit : e)),
      };
      return commit(state, state.doc, next);
    }

    case 'updatePillar': {
      const pillar = state.doc.pillars.find((p) => p.id === action.pillarId);
      if (!pillar) {
        return state;
      }
      const nextPillar: Pillar = { ...pillar, ...action.patch };
      if (
        round1(nextPillar.startX) === round1(nextPillar.endX) &&
        round1(nextPillar.startY) === round1(nextPillar.endY)
      ) {
        return state;
      }
      if (
        !isRectInsideBounds(
          state.doc,
          nextPillar.startX,
          nextPillar.startY,
          nextPillar.endX,
          nextPillar.endY,
        )
      ) {
        return state;
      }
      const next = {
        ...state.doc,
        pillars: state.doc.pillars.map((p) => (p.id === pillar.id ? nextPillar : p)),
      };
      return commit(state, state.doc, next);
    }

    case 'updateFabric': {
      const fabric = state.doc.fabrics.find((f) => f.id === action.fabricId);
      if (!fabric) {
        return state;
      }
      const nextFabric: Fabric = { ...fabric, ...action.patch };
      if (
        round1(nextFabric.startX) === round1(nextFabric.endX) &&
        round1(nextFabric.startY) === round1(nextFabric.endY)
      ) {
        return state;
      }
      if (
        !isRectInsideBounds(
          state.doc,
          nextFabric.startX,
          nextFabric.startY,
          nextFabric.endX,
          nextFabric.endY,
        )
      ) {
        return state;
      }
      const next = {
        ...state.doc,
        fabrics: state.doc.fabrics.map((f) => (f.id === fabric.id ? nextFabric : f)),
      };
      return commit(state, state.doc, next);
    }

    case 'updateText': {
      const text = state.doc.layoutTexts.find((t) => t.id === action.textId);
      if (!text) {
        return state;
      }
      const nextText: LayoutText = { ...text, ...action.patch };
      if (!isInsideBounds(state.doc, nextText.x, nextText.y)) {
        return state;
      }
      const next = {
        ...state.doc,
        layoutTexts: state.doc.layoutTexts.map((t) => (t.id === text.id ? nextText : t)),
      };
      return commit(state, state.doc, next);
    }

    case 'updateOutsideWall': {
      const wall = state.doc.outsideWalls.find((w) => w.id === action.wallId);
      if (!wall) {
        return state;
      }
      const nextWall: OutsideWall = { ...wall, ...action.patch };
      if (
        round1(nextWall.startX) === round1(nextWall.endX) &&
        round1(nextWall.startY) === round1(nextWall.endY)
      ) {
        return state;
      }
      if (
        !isRectInsideBounds(
          state.doc,
          nextWall.startX,
          nextWall.startY,
          nextWall.endX,
          nextWall.endY,
        )
      ) {
        return state;
      }
      const next = {
        ...state.doc,
        outsideWalls: state.doc.outsideWalls.map((w) => (w.id === wall.id ? nextWall : w)),
      };
      return commit(state, state.doc, next);
    }

    case 'undo':
      return applyUndo(state);

    case 'redo':
      return applyRedo(state);

    case 'setError':
      return { ...state, error: action.message, errorNonce: state.errorNonce + 1 };

    case 'setValidationProblems':
      return { ...state, validationProblems: action.problems };

    case 'clearSelection':
      return { ...state, selection: emptySelection(), snapHint: null };

    case 'escape': {
      if (state.draft) {
        return { ...state, draft: null, snapHint: null };
      }
      if (state.textDraft) {
        return { ...state, textDraft: null };
      }
      if (
        state.selection.wallIds.length > 0 ||
        state.selection.outsideWallIds.length > 0 ||
        state.selection.exitIds.length > 0 ||
        state.selection.textIds.length > 0 ||
        state.selection.pillarIds.length > 0 ||
        state.selection.fabricIds.length > 0
      ) {
        return { ...state, selection: emptySelection() };
      }
      return state;
    }

    default:
      return state;
  }
}
