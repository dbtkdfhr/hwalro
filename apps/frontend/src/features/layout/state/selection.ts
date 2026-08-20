import type { EditorState } from '../types';
import { emptySelection, toggleId } from '../utils/document';

export interface SelectAtAction {
  wallId: string | null;
  outsideWallId: string | null;
  exitId: string | null;
  textId: string | null;
  pillarId: string | null;
  fabricId: string | null;
  additive: boolean;
}

export function applySelectAt(state: EditorState, action: SelectAtAction): EditorState {
  if (
    action.wallId === null &&
    action.outsideWallId === null &&
    action.exitId === null &&
    action.textId === null &&
    action.pillarId === null &&
    action.fabricId === null
  ) {
    if (action.additive) {
      return state;
    }
    return { ...state, selection: emptySelection(), snapHint: null };
  }
  const { selection } = state;
  let wallIds = selection.wallIds;
  let outsideWallIds = selection.outsideWallIds;
  let exitIds = selection.exitIds;
  let textIds = selection.textIds;
  let pillarIds = selection.pillarIds;
  let fabricIds = selection.fabricIds;
  if (action.wallId !== null) {
    wallIds = action.additive ? toggleId(wallIds, action.wallId) : [action.wallId];
    if (!action.additive) {
      outsideWallIds = [];
      exitIds = [];
      textIds = [];
      pillarIds = [];
      fabricIds = [];
    }
  } else if (action.outsideWallId !== null) {
    outsideWallIds = action.additive
      ? toggleId(outsideWallIds, action.outsideWallId)
      : [action.outsideWallId];
    if (!action.additive) {
      wallIds = [];
      exitIds = [];
      textIds = [];
      pillarIds = [];
      fabricIds = [];
    }
  } else if (action.exitId !== null) {
    exitIds = action.additive ? toggleId(exitIds, action.exitId) : [action.exitId];
    if (!action.additive) {
      wallIds = [];
      outsideWallIds = [];
      textIds = [];
      pillarIds = [];
      fabricIds = [];
    }
  } else if (action.textId !== null) {
    textIds = action.additive ? toggleId(textIds, action.textId) : [action.textId];
    if (!action.additive) {
      wallIds = [];
      outsideWallIds = [];
      exitIds = [];
      pillarIds = [];
      fabricIds = [];
    }
  } else if (action.pillarId !== null) {
    pillarIds = action.additive ? toggleId(pillarIds, action.pillarId) : [action.pillarId];
    if (!action.additive) {
      wallIds = [];
      outsideWallIds = [];
      exitIds = [];
      textIds = [];
      fabricIds = [];
    }
  } else if (action.fabricId !== null) {
    fabricIds = action.additive ? toggleId(fabricIds, action.fabricId) : [action.fabricId];
    if (!action.additive) {
      wallIds = [];
      outsideWallIds = [];
      exitIds = [];
      textIds = [];
      pillarIds = [];
    }
  }
  return {
    ...state,
    selection: { wallIds, outsideWallIds, exitIds, textIds, pillarIds, fabricIds },
    snapHint: null,
  };
}
