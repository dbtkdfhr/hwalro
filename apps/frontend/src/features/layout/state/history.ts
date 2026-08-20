import type { DrawingDocument, EditorState } from '../types';
import { emptySelection } from '../utils/document';

export function commit(
  state: EditorState,
  prev: DrawingDocument,
  next: DrawingDocument,
): EditorState {
  return {
    ...state,
    doc: next,
    past: [...state.past, prev],
    future: [],
    draft: null,
    textDraft: null,
    drag: null,
    snapHint: null,
    error: null,
    validationProblems: [],
  };
}

export function clearInteraction(state: EditorState): EditorState {
  return {
    ...state,
    draft: null,
    textDraft: null,
    drag: null,
    snapHint: null,
    selection: emptySelection(),
  };
}

export function applyUndo(state: EditorState): EditorState {
  if (state.past.length === 0) {
    return state;
  }
  const past = state.past.slice(0, -1);
  const doc = state.past[state.past.length - 1];
  return {
    ...clearInteraction({ ...state, doc }),
    past,
    future: [state.doc, ...state.future],
    validationProblems: [],
  };
}

export function applyRedo(state: EditorState): EditorState {
  if (state.future.length === 0) {
    return state;
  }
  const [doc, ...future] = state.future;
  return {
    ...clearInteraction({ ...state, doc }),
    past: [...state.past, state.doc],
    future,
    validationProblems: [],
  };
}
