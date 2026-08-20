import { describe, expect, it } from 'vitest';
import type { SimulationDrawing } from '../../simulations/types';
import type { ChangeSet } from '../api/layoutSearchApi';
import { applyChangeSet } from './applyChangeSet';

function drawing(): SimulationDrawing {
  return {
    layoutId: 1,
    title: 'test',
    width: 10,
    height: 10,
    outsideBoundary: [{ x: 0, y: 0 }],
    walls: [],
    pillars: [],
    fabrics: [
      { id: 1, name: 'a', startX: 1, startY: 2, endX: 3, endY: 4, rotation: 0 },
      { id: 2, name: 'b', startX: 1, startY: 2, endX: 3, endY: 4, rotation: 0 },
    ],
    layoutTexts: [],
    exits: [],
  };
}

function moveChangeSet(fabricId = 1): ChangeSet {
  return {
    schemaVersion: 1,
    coordinateUnit: 'METER',
    ops: [
      {
        type: 'MOVE_FABRIC',
        fabricId,
        before: { startX: 1, startY: 2, endX: 3, endY: 4, rotation: 0 },
        after: { startX: 2, startY: 2, endX: 4, endY: 4, rotation: 0 },
      },
    ],
  };
}

describe('applyChangeSet', () => {
  it('applies a move by stable fabric id even when geometries are identical', () => {
    const source = drawing();
    const result = applyChangeSet(source, moveChangeSet(2));

    expect(result.error).toBeNull();
    expect(result.drawing?.fabrics[0]).toEqual(source.fabrics[0]);
    expect(result.drawing?.fabrics[1].startX).toBe(2);
  });

  it('returns an explicit failure for an unknown id instead of geometry matching', () => {
    const result = applyChangeSet(drawing(), moveChangeSet(99));

    expect(result.drawing).toBeNull();
    expect(result.error).toContain('99번');
  });

  it('returns an explicit failure when ids are duplicated', () => {
    const source = drawing();
    source.fabrics[1] = { ...source.fabrics[1], id: 1 };

    const result = applyChangeSet(source, moveChangeSet(1));

    expect(result.drawing).toBeNull();
    expect(result.error).toContain('1번');
  });

  it('does not mutate the input drawing', () => {
    const source = drawing();
    const snapshot = JSON.stringify(source);
    applyChangeSet(source, moveChangeSet());
    expect(JSON.stringify(source)).toBe(snapshot);
  });

  it('replays repeated moves for one fabric in order', () => {
    const first = moveChangeSet().ops[0];
    const result = applyChangeSet(drawing(), {
      schemaVersion: 1,
      coordinateUnit: 'METER',
      ops: [
        first,
        {
          ...first,
          before: first.after,
          after: { startX: 3, startY: 2, endX: 5, endY: 4, rotation: 90 },
        },
      ],
    });

    expect(result.error).toBeNull();
    expect(result.drawing?.fabrics[0]).toMatchObject({ startX: 3, endX: 5, rotation: 90 });
  });

  it('rejects a broken before chain instead of showing a preview the server cannot prepare', () => {
    const changeSet = moveChangeSet();
    changeSet.ops[0] = {
      ...changeSet.ops[0],
      before: { ...changeSet.ops[0].before, startX: 1.5 },
    };

    const result = applyChangeSet(drawing(), changeSet);

    expect(result.drawing).toBeNull();
    expect(result.error).toContain('일치하지 않습니다');
  });
});
