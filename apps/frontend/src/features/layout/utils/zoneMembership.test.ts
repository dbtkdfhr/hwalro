import { describe, expect, it } from 'vitest';
import type { LayoutZone } from '../api/layoutMetadataApi';
import type { Fabric, Pillar, Wall } from '../types';
import { groupElementsByZone, zoneOfFabric } from './zoneMembership';

function fabric(id: string, backendId: number | null): Fabric {
  return {
    id,
    backendId,
    name: id,
    startX: 0,
    startY: 0,
    endX: 1,
    endY: 1,
    rotation: 0,
  };
}

function wall(id: string, backendId: number | null): Wall {
  return {
    id,
    backendId,
    name: id,
    startX: 0,
    startY: 0,
    endX: 1,
    endY: 0,
  };
}

function pillar(id: string, backendId: number | null): Pillar {
  return {
    id,
    backendId,
    name: id,
    startX: 0,
    startY: 0,
    endX: 1,
    endY: 1,
    rotation: 0,
  };
}

function zone(zoneId: number, members: LayoutZone['members']): LayoutZone {
  return {
    zoneId,
    name: `구역 ${zoneId}`,
    zoneType: 'WORK',
    rect: { x: 0, y: 0, width: 10, height: 10 },
    assignedUserId: null,
    defaultExitId: null,
    displayOrder: 0,
    members,
  };
}

describe('groupElementsByZone', () => {
  it('벽·기둥·구조물을 종류별 ID로 구역별로 묶는다', () => {
    const grouped = groupElementsByZone(
      [wall('w', 7)],
      [pillar('p', 8)],
      [fabric('a', 20), fabric('b', 21)],
      [
        zone(30, [
          { kind: 'WALL', id: 7 },
          { kind: 'PILLAR', id: 8 },
        ]),
        zone(31, [{ kind: 'FABRIC', id: 21 }]),
      ],
    );

    expect(grouped.zones[0].members.map((member) => member.id)).toEqual(['w', 'p']);
    expect(grouped.zones[1].members.map((member) => member.id)).toEqual(['b']);
    expect(grouped.common.map((member) => member.id)).toEqual(['a']);
  });

  it('같은 숫자라도 종류가 다르면 다른 요소다', () => {
    const grouped = groupElementsByZone(
      [wall('w', 20)],
      [],
      [fabric('a', 20)],
      [zone(30, [{ kind: 'FABRIC', id: 20 }])],
    );

    expect(grouped.zones[0].members.map((member) => member.id)).toEqual(['a']);
    expect(grouped.common.map((member) => member.id)).toEqual(['w']);
  });

  it('멤버십이 없는 요소와 저장 전 요소는 공용으로 분류한다', () => {
    const grouped = groupElementsByZone(
      [],
      [],
      [fabric('a', 20), fabric('b', 21), fabric('new', null)],
      [zone(30, [{ kind: 'FABRIC', id: 20 }])],
    );

    expect(grouped.zones[0].members.map((member) => member.id)).toEqual(['a']);
    expect(grouped.common.map((member) => member.id)).toEqual(['b', 'new']);
  });

  it('구역이 하나도 없으면 전부 공용이다', () => {
    const grouped = groupElementsByZone([], [], [fabric('a', 20)], []);

    expect(grouped.zones).toHaveLength(0);
    expect(grouped.common.map((member) => member.id)).toEqual(['a']);
  });

  it('zoneOfFabric은 fabric 멤버십만 보고 소속 구역을 찾는다', () => {
    const zones = [
      zone(30, [
        { kind: 'WALL', id: 20 },
        { kind: 'FABRIC', id: 21 },
      ]),
    ];

    expect(zoneOfFabric(fabric('a', 21), zones)?.zoneId).toBe(30);
    expect(zoneOfFabric(fabric('b', 20), zones)).toBeNull();
    expect(zoneOfFabric(fabric('new', null), zones)).toBeNull();
  });
});
