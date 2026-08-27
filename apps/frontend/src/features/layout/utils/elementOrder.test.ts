import { describe, expect, it } from 'vitest';
import type { Fabric, Pillar, Wall } from '../types';
import { orderedElements, withAssignedOrder } from './elementOrder';

const wall = (id: string, displayOrder?: number): Wall => ({
  id,
  backendId: null,
  name: id,
  startX: 0,
  startY: 0,
  endX: 1,
  endY: 1,
  displayOrder,
});

const pillar = (id: string, displayOrder?: number): Pillar => ({
  ...wall(id, displayOrder),
  rotation: 0,
});
const fabric = (id: string, displayOrder?: number): Fabric => ({
  ...wall(id, displayOrder),
  rotation: 0,
});

describe('elementOrder', () => {
  it('종류를 섞어 하나의 순서로 정렬한다', () => {
    const merged = orderedElements([wall('w', 3)], [pillar('p', 1)], [fabric('f', 2)]);
    expect(merged.map(({ element }) => element.id)).toEqual(['p', 'f', 'w']);
  });

  it('순서가 없는 요소는 방금 그린 것이므로 맨 위에 둔다', () => {
    const merged = orderedElements([wall('saved', 0), wall('new')], [], [fabric('f', 1)]);
    expect(merged.map(({ element }) => element.id)).toEqual(['saved', 'f', 'new']);
  });

  it('순서 값이 같으면 벽·기둥·구조물 순으로 안정 정렬한다', () => {
    const merged = orderedElements([wall('w', 0)], [pillar('p', 0)], [fabric('f', 0)]);
    expect(merged.map(({ element }) => element.id)).toEqual(['w', 'p', 'f']);
  });

  it('재배치 결과를 각 요소의 순서 값으로 확정한다', () => {
    const merged = orderedElements([wall('w', 5)], [], [fabric('f', 9)]);
    const assigned = withAssignedOrder([merged[1], merged[0]]);
    expect(assigned.fabrics[0].displayOrder).toBe(0);
    expect(assigned.walls[0].displayOrder).toBe(1);
  });
});
