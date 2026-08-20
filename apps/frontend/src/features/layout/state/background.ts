import type { EditorState, Vec2 } from '../types';
import { uid } from '../utils/document';
import { commit } from './history';

export function applyBackgroundInsert(
  state: EditorState,
  image: string,
  aspect: number,
): EditorState {
  const safeAspect = Number.isFinite(aspect) && aspect > 0 ? aspect : 1;
  const width = state.doc.width * 0.7;
  const height = width / safeAspect;
  const next = {
    ...state.doc,
    background: {
      id: uid(),
      image,
      x: (state.doc.width - width) / 2,
      y: (state.doc.height - height) / 2,
      width,
      height,
      opacity: 0.8,
      aspect: safeAspect,
    },
  };
  return commit(state, state.doc, next);
}

export function applyBackgroundDragStart(state: EditorState, point: Vec2): EditorState {
  if (!state.doc.background) {
    return state;
  }
  return {
    ...state,
    drag: {
      kind: 'backgroundMove',
      origin: point,
      originBg: state.doc.background,
      originDoc: state.doc,
    },
  };
}

export function applyBackgroundResizeStart(state: EditorState, point: Vec2): EditorState {
  if (!state.doc.background) {
    return state;
  }
  return {
    ...state,
    drag: {
      kind: 'backgroundResize',
      origin: point,
      originBg: state.doc.background,
      originDoc: state.doc,
    },
  };
}

export function applyBackgroundResize(state: EditorState, width: number): EditorState {
  const bg = state.doc.background;
  if (!bg) {
    return state;
  }
  const safeWidth = Math.max(1, width);
  const next = {
    ...state.doc,
    background: { ...bg, width: safeWidth, height: safeWidth / bg.aspect },
  };
  return commit(state, state.doc, next);
}

export function applyBackgroundOpacity(state: EditorState, opacity: number): EditorState {
  const bg = state.doc.background;
  if (!bg) {
    return state;
  }
  const next = {
    ...state.doc,
    background: { ...bg, opacity: Math.min(1, Math.max(0.1, opacity)) },
  };
  return commit(state, state.doc, next);
}

export function applyBackgroundRemove(state: EditorState): EditorState {
  if (!state.doc.background) {
    return state;
  }
  const next = { ...state.doc, background: null };
  return commit(state, state.doc, next);
}
