import { useEffect, useRef, useState } from 'react';
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

function readBackgroundFile(file: File): Promise<{ image: string; aspect: number }> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('파일을 읽을 수 없습니다.'));
    reader.onload = () => {
      const dataUrl = typeof reader.result === 'string' ? reader.result : null;
      if (dataUrl === null) {
        reject(new Error('파일을 읽을 수 없습니다.'));
        return;
      }
      const img = new window.Image();
      img.onerror = () => reject(new Error('이미지 파일만 지원합니다.'));
      img.onload = () => {
        if (img.naturalWidth > 0 && img.naturalHeight > 0) {
          resolve({ image: dataUrl, aspect: img.naturalWidth / img.naturalHeight });
        } else {
          reject(new Error('이미지 파일만 지원합니다.'));
        }
      };
      img.src = dataUrl;
    };
    reader.readAsDataURL(file);
  });
}

function BackgroundSection({ state, dispatch }: SettingsPanelProps) {
  const bg = state.doc.background;
  const inputRef = useRef<HTMLInputElement | null>(null);

  const pickFile = (file: File | undefined) => {
    if (!file) {
      return;
    }
    void readBackgroundFile(file).then(
      ({ image, aspect }) => dispatch({ type: 'backgroundInsert', image, aspect }),
      (reason: unknown) =>
        dispatch({
          type: 'setError',
          message: reason instanceof Error ? reason.message : '이미지를 읽을 수 없습니다.',
        }),
    );
  };

  return (
    <section className="mt-4 border-t border-panel-divider pt-4">
      <h3 className="text-sm font-bold text-panel-text">배경</h3>
      <p className="mt-0.5 text-xs text-panel-muted">
        저장되지 않고 화면에만 표시됩니다. 배경 도구로 이동·크기 조절.
      </p>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(event) => {
          pickFile(event.currentTarget.files?.[0]);
          event.currentTarget.value = '';
        }}
      />
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        className="mt-2 flex h-8 w-full items-center justify-center rounded-md border border-panel-border bg-panel-soft text-sm font-bold text-panel-text transition-colors hover:bg-panel focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
      >
        {bg ? '배경 교체' : '배경 이미지 추가'}
      </button>
      {bg && (
        <div className="mt-3 space-y-3">
          <label className="block">
            <span className="text-xs text-panel-muted">투명도</span>
            <input
              type="range"
              min={0.1}
              max={1}
              step={0.05}
              value={bg.opacity}
              onChange={(event) =>
                dispatch({ type: 'backgroundOpacity', opacity: Number(event.currentTarget.value) })
              }
              className="mt-1 w-full"
            />
          </label>
          <button
            type="button"
            onClick={() => dispatch({ type: 'backgroundRemove' })}
            className="flex h-8 w-full items-center justify-center rounded-md border border-danger/40 bg-panel-soft text-sm font-bold text-danger transition-colors hover:bg-panel focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
          >
            배경 제거
          </button>
        </div>
      )}
    </section>
  );
}

export function SettingsPanel({ state, dispatch }: SettingsPanelProps) {
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
          <InfoRow label="외각벽" value={`${doc.outsideWalls.length}개`} />
          <InfoRow label="비상구" value={`${doc.exits.length}개`} />
          <InfoRow label="기둥" value={`${doc.pillars.length}개`} />
          <InfoRow label="구조물" value={`${doc.fabrics.length}개`} />
          <InfoRow label="텍스트" value={`${doc.layoutTexts.length}개`} />
        </div>
      </section>
      <BackgroundSection state={state} dispatch={dispatch} />
    </section>
  );
}
