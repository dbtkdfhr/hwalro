// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createInitialState } from '../state/editorReducer';
import type { LayerElement } from '../utils/zoneMembership';
import { LayersPanel } from './LayersPanel';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  document.body.replaceChildren();
});

function transfer(): DataTransfer {
  const values = new Map<string, string>();
  return {
    effectAllowed: 'uninitialized',
    dropEffect: 'none',
    files: [] as unknown as FileList,
    items: [] as unknown as DataTransferItemList,
    types: [],
    clearData: (format?: string) => {
      if (format) values.delete(format);
      else values.clear();
    },
    getData: (format: string) => values.get(format) ?? '',
    setData: (format: string, data: string) => {
      values.set(format, data);
    },
    setDragImage: () => undefined,
  };
}

function dragEvent(type: string, dataTransfer: DataTransfer, clientY = 0, clientX = 0): Event {
  const event = new Event(type, { bubbles: true, cancelable: true });
  Object.defineProperties(event, {
    dataTransfer: { value: dataTransfer },
    clientY: { value: clientY },
    clientX: { value: clientX },
  });
  return event;
}

describe('LayersPanel common reorder', () => {
  it('shows an after marker and dispatches the explicit insertion position', () => {
    const initial = createInitialState();
    const state = {
      ...initial,
      doc: {
        ...initial.doc,
        walls: ['벽 1', '벽 2', '벽 3'].map((name, index) => ({
          id: `w${index + 1}`,
          backendId: index + 1,
          name,
          startX: index,
          startY: 0,
          endX: index + 1,
          endY: 0,
        })),
      },
    };
    const dispatch = vi.fn();
    const changeMembership = vi.fn<(element: LayerElement, zoneId: number | null) => void>();
    act(() => {
      root.render(
        <LayersPanel
          state={state}
          dispatch={dispatch}
          zones={[]}
          selectedZoneId={null}
          onSelectZone={vi.fn()}
          employeeNameById={{}}
          orderLocked={false}
          onChangeMembership={changeMembership}
          onGroupSelectionIntoZone={vi.fn()}
          onMoveZoneOrder={vi.fn()}
          onCenterPoint={vi.fn()}
        />,
      );
    });
    const source = container.querySelector<HTMLButtonElement>('button[aria-label="벽 1 선택"]');
    const target = container.querySelector<HTMLButtonElement>('button[aria-label="벽 3 선택"]');
    expect(source).not.toBeNull();
    expect(target).not.toBeNull();
    const targetRow = target?.closest('li');
    Object.defineProperty(targetRow, 'getBoundingClientRect', {
      value: () => ({ top: 100, right: 200, bottom: 130, left: 0, width: 200, height: 30 }),
    });
    const dataTransfer = transfer();

    act(() => source?.dispatchEvent(dragEvent('dragstart', dataTransfer)));
    act(() => target?.dispatchEvent(dragEvent('dragover', dataTransfer, 129, 100)));
    expect(targetRow?.dataset.dropPosition).toBe('after');
    expect(targetRow?.querySelector('.layout-layer-insertion--after')).not.toBeNull();

    act(() => target?.dispatchEvent(dragEvent('dragleave', dataTransfer, 115, 100)));
    expect(targetRow?.dataset.dropPosition).toBe('after');

    act(() => target?.dispatchEvent(dragEvent('drop', dataTransfer, 129, 100)));
    expect(dispatch).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'reorderElements',
        draggedId: 'w1',
        targetId: 'w3',
        position: 'after',
      }),
    );
    expect(changeMembership).toHaveBeenCalledWith(expect.objectContaining({ id: 'w1' }), null);
    expect(targetRow?.dataset.dropPosition).toBeUndefined();
  });

  it('keeps a single insertion marker and clears it when the drag ends', () => {
    const initial = createInitialState();
    const state = {
      ...initial,
      doc: {
        ...initial.doc,
        walls: ['벽 1', '벽 2', '벽 3'].map((name, index) => ({
          id: `w${index + 1}`,
          backendId: index + 1,
          name,
          startX: index,
          startY: 0,
          endX: index + 1,
          endY: 0,
        })),
      },
    };
    act(() => {
      root.render(
        <LayersPanel
          state={state}
          dispatch={vi.fn()}
          zones={[]}
          selectedZoneId={null}
          onSelectZone={vi.fn()}
          employeeNameById={{}}
          orderLocked={false}
          onChangeMembership={vi.fn()}
          onGroupSelectionIntoZone={vi.fn()}
          onMoveZoneOrder={vi.fn()}
          onCenterPoint={vi.fn()}
        />,
      );
    });
    const rowOf = (label: string) =>
      container.querySelector<HTMLButtonElement>(`button[aria-label="${label} 선택"]`);
    const source = rowOf('벽 1');
    const second = rowOf('벽 2');
    const third = rowOf('벽 3');
    for (const row of [second?.closest('li'), third?.closest('li')]) {
      Object.defineProperty(row, 'getBoundingClientRect', {
        value: () => ({ top: 100, right: 200, bottom: 130, left: 0, width: 200, height: 30 }),
      });
    }
    const dataTransfer = transfer();

    act(() => source?.dispatchEvent(dragEvent('dragstart', dataTransfer)));
    act(() => second?.dispatchEvent(dragEvent('dragover', dataTransfer, 129, 100)));
    // dragleave 없이 다음 행으로 넘어가도 삽입선은 한 곳에만 남는다.
    act(() => third?.dispatchEvent(dragEvent('dragover', dataTransfer, 101, 100)));
    expect(second?.closest('li')?.dataset.dropPosition).toBeUndefined();
    expect(third?.closest('li')?.dataset.dropPosition).toBe('before');
    expect(container.querySelectorAll('.layout-layer-insertion')).toHaveLength(1);

    // 드롭 없이 드래그가 끝나도 삽입선이 남지 않는다.
    act(() => source?.dispatchEvent(dragEvent('dragend', dataTransfer)));
    expect(container.querySelectorAll('.layout-layer-insertion')).toHaveLength(0);
    expect(third?.closest('li')?.dataset.dropPosition).toBeUndefined();
  });

  it('makes the current selection prominent and collapses common and zone groups', () => {
    const initial = createInitialState();
    const state = {
      ...initial,
      selection: { ...initial.selection, wallIds: ['w1'] },
      doc: {
        ...initial.doc,
        walls: [
          {
            id: 'w1',
            backendId: 1,
            name: '구역 벽',
            startX: 0,
            startY: 0,
            endX: 1,
            endY: 0,
          },
          {
            id: 'w2',
            backendId: 2,
            name: '공통 벽',
            startX: 2,
            startY: 0,
            endX: 3,
            endY: 0,
          },
        ],
      },
    };
    act(() => {
      root.render(
        <LayersPanel
          state={state}
          dispatch={vi.fn()}
          zones={[
            {
              zoneId: 1,
              name: '전시 구역',
              zoneType: 'WORK',
              rect: { x: 0, y: 0, width: 10, height: 10 },
              assignedUserId: null,
              defaultExitId: null,
              displayOrder: 0,
              members: [{ kind: 'WALL', id: 1 }],
            },
          ]}
          selectedZoneId={null}
          onSelectZone={vi.fn()}
          employeeNameById={{}}
          orderLocked={false}
          onChangeMembership={vi.fn()}
          onGroupSelectionIntoZone={vi.fn()}
          onMoveZoneOrder={vi.fn()}
          onCenterPoint={vi.fn()}
        />,
      );
    });

    const selectedRow = container.querySelector<HTMLButtonElement>(
      'button[aria-label="구역 벽 선택"]',
    );
    expect(selectedRow?.classList.contains('is-selected')).toBe(true);

    const zoneToggle = container.querySelector<HTMLButtonElement>(
      'button[aria-label="전시 구역 접기"]',
    );
    act(() => zoneToggle?.click());
    expect(container.querySelector('button[aria-label="구역 벽 선택"]')).toBeNull();
    act(() =>
      container.querySelector<HTMLButtonElement>('button[aria-label="전시 구역 펴기"]')?.click(),
    );
    expect(container.querySelector('button[aria-label="구역 벽 선택"]')).not.toBeNull();

    const commonToggle = container.querySelector<HTMLButtonElement>(
      'button[aria-controls="layout-layer-common"]',
    );
    act(() => commonToggle?.click());
    expect(container.querySelector('button[aria-label="공통 벽 선택"]')).toBeNull();
    act(() => commonToggle?.click());
    expect(container.querySelector('button[aria-label="공통 벽 선택"]')).not.toBeNull();
  });

  it('shows an insertion marker across kinds so a wall can be ordered against a pillar', () => {
    const initial = createInitialState();
    const state = {
      ...initial,
      doc: {
        ...initial.doc,
        walls: [
          {
            id: 'w1',
            backendId: 1,
            name: '공통 벽',
            startX: 0,
            startY: 0,
            endX: 1,
            endY: 0,
          },
        ],
        pillars: [
          {
            id: 'p1',
            backendId: 2,
            name: '구역 기둥',
            startX: 2,
            startY: 0,
            endX: 3,
            endY: 1,
            rotation: 0,
          },
        ],
      },
    };
    const changeMembership = vi.fn<(element: LayerElement, zoneId: number | null) => void>();
    act(() => {
      root.render(
        <LayersPanel
          state={state}
          dispatch={vi.fn()}
          zones={[
            {
              zoneId: 1,
              name: '전시 구역',
              zoneType: 'WORK',
              rect: { x: 0, y: 0, width: 10, height: 10 },
              assignedUserId: null,
              defaultExitId: null,
              displayOrder: 0,
              members: [{ kind: 'PILLAR', id: 2 }],
            },
          ]}
          selectedZoneId={null}
          onSelectZone={vi.fn()}
          employeeNameById={{}}
          orderLocked={false}
          onChangeMembership={changeMembership}
          onGroupSelectionIntoZone={vi.fn()}
          onMoveZoneOrder={vi.fn()}
          onCenterPoint={vi.fn()}
        />,
      );
    });

    const source = container.querySelector<HTMLButtonElement>('button[aria-label="공통 벽 선택"]');
    const target = container.querySelector<HTMLButtonElement>(
      'button[aria-label="구역 기둥 선택"]',
    );
    const targetRow = target?.closest('li');
    Object.defineProperty(targetRow, 'getBoundingClientRect', {
      value: () => ({ top: 100, right: 200, bottom: 130, left: 0, width: 200, height: 30 }),
    });
    const dataTransfer = transfer();

    act(() => source?.dispatchEvent(dragEvent('dragstart', dataTransfer)));
    act(() => target?.dispatchEvent(dragEvent('dragover', dataTransfer, 101, 100)));
    expect(targetRow?.dataset.dropPosition).toBe('before');
    expect(targetRow?.querySelector('.layout-layer-insertion--before')).not.toBeNull();

    act(() => target?.dispatchEvent(dragEvent('drop', dataTransfer, 101, 100)));
    expect(changeMembership).toHaveBeenCalledWith(expect.objectContaining({ id: 'w1' }), 1);
  });

  it('shows a first-position drop slot when moving into an empty common group', () => {
    const initial = createInitialState();
    const state = {
      ...initial,
      doc: {
        ...initial.doc,
        walls: [
          {
            id: 'w1',
            backendId: 1,
            name: '구역 벽',
            startX: 0,
            startY: 0,
            endX: 1,
            endY: 0,
          },
        ],
      },
    };
    const changeMembership = vi.fn<(element: LayerElement, zoneId: number | null) => void>();
    act(() => {
      root.render(
        <LayersPanel
          state={state}
          dispatch={vi.fn()}
          zones={[
            {
              zoneId: 1,
              name: '전시 구역',
              zoneType: 'WORK',
              rect: { x: 0, y: 0, width: 10, height: 10 },
              assignedUserId: null,
              defaultExitId: null,
              displayOrder: 0,
              members: [{ kind: 'WALL', id: 1 }],
            },
          ]}
          selectedZoneId={null}
          onSelectZone={vi.fn()}
          employeeNameById={{}}
          orderLocked={false}
          onChangeMembership={changeMembership}
          onGroupSelectionIntoZone={vi.fn()}
          onMoveZoneOrder={vi.fn()}
          onCenterPoint={vi.fn()}
        />,
      );
    });

    const source = container.querySelector<HTMLButtonElement>('button[aria-label="구역 벽 선택"]');
    const emptyDrop = container.querySelector<HTMLLIElement>('.layout-layer-empty-drop');
    Object.defineProperty(emptyDrop, 'getBoundingClientRect', {
      value: () => ({ top: 100, right: 200, bottom: 140, left: 0, width: 200, height: 40 }),
    });
    const dataTransfer = transfer();

    act(() => source?.dispatchEvent(dragEvent('dragstart', dataTransfer)));
    act(() => emptyDrop?.dispatchEvent(dragEvent('dragover', dataTransfer, 120, 100)));
    expect(emptyDrop?.classList.contains('is-active')).toBe(true);
    expect(emptyDrop?.querySelector('.layout-layer-insertion--before')).not.toBeNull();

    act(() => emptyDrop?.dispatchEvent(dragEvent('drop', dataTransfer, 120, 100)));
    expect(changeMembership).toHaveBeenCalledWith(expect.objectContaining({ id: 'w1' }), null);
  });

  it('keeps employee rows selectable without drag or zone membership menus', () => {
    const initial = createInitialState();
    const state = {
      ...initial,
      doc: {
        ...initial.doc,
        fabrics: [
          {
            id: 'f1',
            backendId: 20,
            name: '진열대',
            startX: 1,
            startY: 1,
            endX: 3,
            endY: 2,
            rotation: 0,
          },
        ],
      },
    };
    const dispatch = vi.fn();
    act(() => {
      root.render(
        <LayersPanel
          state={state}
          dispatch={dispatch}
          zones={[]}
          selectedZoneId={null}
          onSelectZone={vi.fn()}
          employeeNameById={{}}
          orderLocked={false}
          membershipEditable={false}
          onChangeMembership={vi.fn()}
          onGroupSelectionIntoZone={vi.fn()}
          onMoveZoneOrder={vi.fn()}
          onCenterPoint={vi.fn()}
        />,
      );
    });

    const row = Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find(
      (button) => button.getAttribute('aria-label') === '진열대 선택',
    );
    expect(row).toBeDefined();
    expect(row?.getAttribute('draggable')).toBe('false');
    expect(row?.getAttribute('aria-haspopup')).toBeNull();
    act(() => row?.click());
    expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({ type: 'selectAt' }));
  });
});
