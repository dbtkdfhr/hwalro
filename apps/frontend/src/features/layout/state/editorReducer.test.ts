import { describe, expect, it } from 'vitest';
import { createInitialState, editorReducer } from './editorReducer';
import { orderedElements } from '../utils/elementOrder';

const wall = (id: string) => ({
  id,
  backendId: Number(id.slice(1)),
  name: id,
  startX: 0,
  startY: 0,
  endX: 1,
  endY: 1,
});

describe('editor layer reorder', () => {
  it('commits an explicit relative position and undo restores it', () => {
    const initial = createInitialState();
    const loaded = {
      ...initial,
      doc: { ...initial.doc, walls: [wall('w1'), wall('w2'), wall('w3')] },
    };
    const reordered = editorReducer(loaded, {
      type: 'reorderElements',
      draggedId: 'w1',
      targetId: 'w3',
      position: 'after',
    });

    expect(reordered.doc.walls.map(({ id }) => id)).toEqual(['w2', 'w3', 'w1']);
    expect(reordered.past).toHaveLength(1);
    expect(editorReducer(reordered, { type: 'undo' }).doc.walls.map(({ id }) => id)).toEqual([
      'w1',
      'w2',
      'w3',
    ]);
  });

  it('does not commit no-op or unknown-target drops', () => {
    const initial = createInitialState();
    const loaded = {
      ...initial,
      doc: { ...initial.doc, walls: [wall('w1'), wall('w2')] },
    };
    expect(
      editorReducer(loaded, {
        type: 'reorderElements',
        draggedId: 'w1',
        targetId: 'w2',
        position: 'before',
      }),
    ).toBe(loaded);
    expect(
      editorReducer(loaded, {
        type: 'reorderElements',
        draggedId: 'w1',
        targetId: '없는-요소',
        position: 'before',
      }),
    ).toBe(loaded);
  });

  it('reorders across kinds so a wall can sit above a fabric', () => {
    const initial = createInitialState();
    const loaded = {
      ...initial,
      doc: {
        ...initial.doc,
        walls: [wall('w1')],
        fabrics: [{ ...wall('f1'), rotation: 0 }],
      },
    };
    const reordered = editorReducer(loaded, {
      type: 'reorderElements',
      draggedId: 'w1',
      targetId: 'f1',
      position: 'after',
    });

    // 벽이 구조물보다 뒤(=위)로 가야 하므로 순서 값이 더 커진다.
    expect(reordered.doc.walls[0].displayOrder).toBe(1);
    expect(reordered.doc.fabrics[0].displayOrder).toBe(0);
    expect(
      orderedElements(reordered.doc.walls, reordered.doc.pillars, reordered.doc.fabrics).map(
        ({ element }) => element.id,
      ),
    ).toEqual(['f1', 'w1']);
  });
});

describe('저장 응답의 서버 ID 반영', () => {
  it('새로 그린 요소가 저장 직후 서버 ID를 갖는다', () => {
    const initial = createInitialState();
    const loaded = {
      ...initial,
      doc: {
        ...initial.doc,
        walls: [{ ...wall('w1'), backendId: null }],
        exits: [
          {
            id: 'e1',
            backendId: null,
            name: '비상구 1',
            startX: 0,
            startY: 0,
            endX: 1,
            endY: 0,
          },
        ],
        fabrics: [{ ...wall('f1'), backendId: null, rotation: 0 }],
      },
    };
    const adopted = editorReducer(loaded, {
      type: 'adoptSavedIds',
      walls: [11],
      exits: [33],
      pillars: [],
      fabrics: [22],
    });

    expect(adopted.doc.walls[0].backendId).toBe(11);
    expect(adopted.doc.exits[0].backendId).toBe(33);
    expect(adopted.doc.fabrics[0].backendId).toBe(22);
    // 사용자 편집이 아니므로 실행 취소 이력에 남지 않는다.
    expect(adopted.past).toHaveLength(0);
  });

  it('저장 중 요소 수가 달라졌으면 위치가 어긋나므로 반영하지 않는다', () => {
    const initial = createInitialState();
    const loaded = {
      ...initial,
      doc: { ...initial.doc, walls: [wall('w1'), wall('w2')] },
    };
    expect(
      editorReducer(loaded, {
        type: 'adoptSavedIds',
        walls: [11],
        exits: [],
        pillars: [],
        fabrics: [],
      }),
    ).toBe(loaded);
  });
});
