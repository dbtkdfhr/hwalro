import { describe, expect, it } from 'vitest';
import type { LayoutZone } from '../api/layoutMetadataApi';
import type { Fabric } from '../types';
import { hitTestElements } from './hitTest';

function fabric(id: string, backendId: number): Fabric {
  return {
    id,
    backendId,
    name: id,
    startX: 0,
    startY: 0,
    endX: 10,
    endY: 10,
    rotation: 0,
  };
}

function zone(zoneId: number): LayoutZone {
  return {
    zoneId,
    name: `구역 ${zoneId}`,
    zoneType: 'WORK',
    rect: { x: 0, y: 0, width: 10, height: 10 },
    assignedUserId: null,
    defaultExitId: null,
    displayOrder: 0,
    members: [],
  };
}

describe('hitTestElements zones', () => {
  it('구역은 겹치는 구조물보다 우선순위가 낮다', () => {
    const hit = hitTestElements({ x: 5, y: 5 }, [], [], [], [], [fabric('f1', 1)], [], 1, [
      zone(30),
    ]);

    expect(hit.fabricId).toBe('f1');
    expect(hit.zoneId).toBeNull();
  });

  it('아무것도 없는 곳의 클릭은 구역을 잡는다', () => {
    const hit = hitTestElements({ x: 5, y: 5 }, [], [], [], [], [], [], 1, [zone(30)]);

    expect(hit.zoneId).toBe(30);
  });

  it('zones를 넘기지 않아도 동작한다', () => {
    const hit = hitTestElements({ x: 50, y: 50 }, [], [], [], [], [], [], 1);

    expect(hit.zoneId).toBeNull();
    expect(hit.fabricId).toBeNull();
  });
});
