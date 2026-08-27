import { memo, useEffect, useState } from 'react';
import type { Dispatch } from 'react';
import type { EditorState, Exit, Fabric, LayoutText, OutsideWall, Pillar, Wall } from '../types';
import type { EditorAction } from '../state/editorReducer';
import { round1 } from '../utils/geometry';

interface SettingsPanelProps {
  state: EditorState;
  dispatch: Dispatch<EditorAction>;
}

function selectedWall(state: EditorState): Wall | null {
  const id = state.selection.wallIds[0];
  if (!id) {
    return null;
  }
  return state.doc.walls.find((wall) => wall.id === id) ?? null;
}

function selectedOutsideWall(state: EditorState): OutsideWall | null {
  const id = state.selection.outsideWallIds[0];
  if (!id) {
    return null;
  }
  return state.doc.outsideWalls.find((wall) => wall.id === id) ?? null;
}

function selectedPillar(state: EditorState): Pillar | null {
  const id = state.selection.pillarIds[0];
  if (!id) {
    return null;
  }
  return state.doc.pillars.find((pillar) => pillar.id === id) ?? null;
}

function selectedFabric(state: EditorState): Fabric | null {
  const id = state.selection.fabricIds[0];
  if (!id) {
    return null;
  }
  return state.doc.fabrics.find((fabric) => fabric.id === id) ?? null;
}

function selectedText(state: EditorState): LayoutText | null {
  const id = state.selection.textIds[0];
  if (!id) {
    return null;
  }
  return state.doc.layoutTexts.find((text) => text.id === id) ?? null;
}

function selectedExit(state: EditorState): Exit | null {
  const id = state.selection.exitIds[0];
  if (!id) {
    return null;
  }
  return state.doc.exits.find((exit) => exit.id === id) ?? null;
}

interface NumberFieldProps {
  label: string;
  value: number;
  unit?: string;
  onChange: (value: number) => void;
}

function NumberField({ label, value, unit = 'm', onChange }: NumberFieldProps) {
  const [draft, setDraft] = useState(String(round1(value)));

  useEffect(() => {
    setDraft(String(round1(value)));
  }, [value]);

  const commitDraft = () => {
    const num = Number(draft);
    if (Number.isFinite(num)) {
      onChange(round1(num));
    } else {
      setDraft(String(round1(value)));
    }
  };

  return (
    <label className="block min-w-0">
      <span className="block text-xs text-panel-muted">{label}</span>
      <span className="mt-1 flex h-8 items-center rounded-md border border-panel-border bg-panel-soft focus-within:ring-2 focus-within:ring-focus-ring">
        <input
          type="number"
          step={0.1}
          value={draft}
          onChange={(event) => setDraft(event.currentTarget.value)}
          onBlur={commitDraft}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              commitDraft();
            }
          }}
          className="h-full w-full min-w-0 bg-transparent px-2 font-mono text-sm text-panel-text outline-none"
        />
        <span className="shrink-0 pr-2 font-mono text-xs text-panel-faint">{unit}</span>
      </span>
    </label>
  );
}

interface WallFieldsProps {
  wall: Wall;
  dispatch: Dispatch<EditorAction>;
}

function WallFields({ wall, dispatch }: WallFieldsProps) {
  const update = (patch: Partial<Pick<Wall, 'startX' | 'startY' | 'endX' | 'endY'>>) =>
    dispatch({ type: 'updateWall', wallId: wall.id, patch });
  return (
    <section>
      <h3 className="text-sm font-bold text-panel-text">선택 요소</h3>
      <p className="mt-0.5 text-sm text-panel-text">{wall.name}</p>
      <div className="mt-3 grid grid-cols-2 gap-3">
        <NumberField label="시작점 X" value={wall.startX} onChange={(x) => update({ startX: x })} />
        <NumberField label="시작점 Y" value={wall.startY} onChange={(y) => update({ startY: y })} />
        <NumberField label="끝점 X" value={wall.endX} onChange={(x) => update({ endX: x })} />
        <NumberField label="끝점 Y" value={wall.endY} onChange={(y) => update({ endY: y })} />
      </div>
    </section>
  );
}

interface OutsideWallFieldsProps {
  wall: OutsideWall;
  dispatch: Dispatch<EditorAction>;
}

function OutsideWallFields({ wall, dispatch }: OutsideWallFieldsProps) {
  const update = (patch: Partial<Pick<OutsideWall, 'startX' | 'startY' | 'endX' | 'endY'>>) =>
    dispatch({ type: 'updateOutsideWall', wallId: wall.id, patch });
  return (
    <section>
      <h3 className="text-sm font-bold text-panel-text">선택 요소</h3>
      <p className="mt-0.5 text-sm text-panel-text">{wall.name}</p>
      <div className="mt-3 grid grid-cols-2 gap-3">
        <NumberField label="시작점 X" value={wall.startX} onChange={(x) => update({ startX: x })} />
        <NumberField label="시작점 Y" value={wall.startY} onChange={(y) => update({ startY: y })} />
        <NumberField label="끝점 X" value={wall.endX} onChange={(x) => update({ endX: x })} />
        <NumberField label="끝점 Y" value={wall.endY} onChange={(y) => update({ endY: y })} />
      </div>
    </section>
  );
}

interface ExitFieldsProps {
  exit: Exit;
  dispatch: Dispatch<EditorAction>;
}

function ExitFields({ exit, dispatch }: ExitFieldsProps) {
  const update = (patch: Partial<Pick<Exit, 'startX' | 'startY' | 'endX' | 'endY'>>) =>
    dispatch({ type: 'updateExit', exitId: exit.id, patch });
  return (
    <section>
      <h3 className="text-sm font-bold text-panel-text">선택 요소</h3>
      <p className="mt-0.5 text-sm text-panel-text">{exit.name}</p>
      <div className="mt-3 grid grid-cols-2 gap-3">
        <NumberField label="시작점 X" value={exit.startX} onChange={(x) => update({ startX: x })} />
        <NumberField label="시작점 Y" value={exit.startY} onChange={(y) => update({ startY: y })} />
        <NumberField label="끝점 X" value={exit.endX} onChange={(x) => update({ endX: x })} />
        <NumberField label="끝점 Y" value={exit.endY} onChange={(y) => update({ endY: y })} />
      </div>
    </section>
  );
}

interface RectFieldsProps {
  element: Pillar | Fabric;
  dispatch: Dispatch<EditorAction>;
  kind: 'pillar' | 'fabric';
}

function RectFields({ element, dispatch, kind }: RectFieldsProps) {
  const update = (
    patch: Partial<Pick<Pillar | Fabric, 'startX' | 'startY' | 'endX' | 'endY' | 'rotation'>>,
  ) =>
    dispatch(
      kind === 'pillar'
        ? { type: 'updatePillar', pillarId: element.id, patch }
        : { type: 'updateFabric', fabricId: element.id, patch },
    );
  return (
    <section>
      <h3 className="text-sm font-bold text-panel-text">선택 요소</h3>
      <p className="mt-0.5 text-sm text-panel-text">{element.name}</p>
      <div className="mt-3 grid grid-cols-2 gap-3">
        <NumberField
          label="시작점 X"
          value={element.startX}
          onChange={(x) => update({ startX: x })}
        />
        <NumberField
          label="시작점 Y"
          value={element.startY}
          onChange={(y) => update({ startY: y })}
        />
        <NumberField label="끝점 X" value={element.endX} onChange={(x) => update({ endX: x })} />
        <NumberField label="끝점 Y" value={element.endY} onChange={(y) => update({ endY: y })} />
        <NumberField
          label="회전"
          value={element.rotation}
          unit="°"
          onChange={(rotation) => update({ rotation })}
        />
      </div>
    </section>
  );
}

interface TextFieldsProps {
  text: LayoutText;
  dispatch: Dispatch<EditorAction>;
}

function TextFields({ text, dispatch }: TextFieldsProps) {
  const update = (patch: Partial<Pick<LayoutText, 'x' | 'y'>>) =>
    dispatch({ type: 'updateText', textId: text.id, patch });
  return (
    <section>
      <h3 className="text-sm font-bold text-panel-text">선택 요소</h3>
      <p className="mt-0.5 truncate text-sm text-panel-text">{text.text}</p>
      <div className="mt-3 grid grid-cols-2 gap-3">
        <NumberField label="X 위치" value={text.x} onChange={(x) => update({ x })} />
        <NumberField label="Y 위치" value={text.y} onChange={(y) => update({ y })} />
      </div>
    </section>
  );
}

interface InfoRowProps {
  label: string;
  value: string;
}

function InfoRow({ label, value }: InfoRowProps) {
  return (
    <div className="flex items-center justify-between gap-3 py-1.5">
      <span className="shrink-0 text-xs text-panel-muted">{label}</span>
      <span className="truncate font-mono text-sm text-panel-text">{value}</span>
    </div>
  );
}

export const SettingsPanel = memo(
  function SettingsPanel({ state, dispatch }: SettingsPanelProps) {
    const wall = selectedWall(state);
    const outsideWall = wall === null ? selectedOutsideWall(state) : null;
    const exit = outsideWall === null ? selectedExit(state) : null;
    const pillar = exit === null ? selectedPillar(state) : null;
    const fabric = pillar === null ? selectedFabric(state) : null;
    const text = fabric === null ? selectedText(state) : null;
    const { doc } = state;
    const hasSelection =
      wall !== null ||
      outsideWall !== null ||
      exit !== null ||
      pillar !== null ||
      fabric !== null ||
      text !== null;

    return (
      <section aria-label="도면 상세 설정" className="layout-settings-content">
        {wall !== null ? (
          <WallFields wall={wall} dispatch={dispatch} />
        ) : outsideWall !== null ? (
          <OutsideWallFields wall={outsideWall} dispatch={dispatch} />
        ) : exit !== null ? (
          <ExitFields exit={exit} dispatch={dispatch} />
        ) : pillar !== null ? (
          <RectFields element={pillar} dispatch={dispatch} kind="pillar" />
        ) : fabric !== null ? (
          <RectFields element={fabric} dispatch={dispatch} kind="fabric" />
        ) : text !== null ? (
          <TextFields text={text} dispatch={dispatch} />
        ) : null}
        <section className={hasSelection ? 'mt-4 border-t border-panel-divider pt-4' : ''}>
          <h3 className="text-sm font-bold text-panel-text">레이어</h3>
          <div className="mt-1">
            <InfoRow
              label="크기"
              value={`${doc.width.toLocaleString('ko-KR')}m × ${doc.height.toLocaleString('ko-KR')}m`}
            />
            <InfoRow label="벽" value={`${doc.walls.length}개`} />
            <InfoRow label="외곽벽" value={`${doc.outsideWalls.length}개`} />
            <InfoRow label="비상구" value={`${doc.exits.length}개`} />
            <InfoRow label="기둥" value={`${doc.pillars.length}개`} />
            <InfoRow label="구조물" value={`${doc.fabrics.length}개`} />
            <InfoRow label="텍스트" value={`${doc.layoutTexts.length}개`} />
          </div>
        </section>
      </section>
    );
  },
  (prev, next) =>
    prev.state.doc === next.state.doc &&
    prev.state.selection === next.state.selection &&
    prev.state.validationProblems === next.state.validationProblems &&
    prev.dispatch === next.dispatch,
);
