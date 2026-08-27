import { describe, expect, it } from 'vitest';
import type { LayoutZone, ZoneRect } from '../api/layoutMetadataApi';
import type { Fabric, Pillar, Wall } from '../types';
import { boundingBoxOf, zoneAsRect, ZONE_PADDING_METERS } from './zoneGeometry';

function wall(startX: number, startY: number, endX: number, endY: number): Wall {
  return {
    id: `w-${startX}-${startY}`,
    backendId: 1,
    name: '벽',
    startX,
    startY,
    endX,
    endY,
  };
}

function pillar(startX: number, startY: number, endX: number, endY: number, rotation = 0): Pillar {
  return {
    id: 'p',
    backendId: 2,
    name: '기둥',
    startX,
    startY,
    endX,
    endY,
    rotation,
  };
}

function fabric(startX: number, startY: number, endX: number, endY: number, rotation = 0): Fabric {
  return {
    id: 'f',
    backendId: 3,
    name: '구조물',
    startX,
    startY,
    endX,
    endY,
    rotation,
  };
}

function zone(rect: ZoneRect): LayoutZone {
  return {
    zoneId: 1,
    name: '구역',
    zoneType: 'WORK',
    rect,
    assignedUserId: null,
    defaultExitId: null,
    displayOrder: 0,
    members: [],
  };
}

describe('zoneAsRect', () => {
  it('Zone 사각형을 회전 없는 RectLike로 바꾼다', () => {
    const rectLike = zoneAsRect(zone({ x: 2, y: 3, width: 4, height: 5 }));

    expect(rectLike).toEqual({ startX: 2, startY: 3, endX: 6, endY: 8, rotation: 0 });
  });
});

describe('boundingBoxOf', () => {
  it('회전된 기둥·구조물의 실제 코너를 포함한다', () => {
    const box = boundingBoxOf(
      [],
      [pillar(0, 0, 2, 2)],
      [fabric(4, 4, 6, 8, Math.PI / 2)],
      100,
      100,
    );

    expect(box).not.toBeNull();
    expect(box!.x).toBe(0);
    expect(box!.y).toBe(0);
    expect(box!.width).toBeGreaterThan(6);
    expect(box!.height).toBeGreaterThan(8);
    expect(box!.x + box!.width).toBeLessThanOrEqual(100);
    expect(box!.y + box!.height).toBeLessThanOrEqual(100);
  });

  it('벽은 선분 끝점이 곧 범위다', () => {
    const box = boundingBoxOf([wall(10, 10, 20, 30)], [], [], 100, 100);

    expect(box).toEqual({
      x: 10 - ZONE_PADDING_METERS,
      y: 10 - ZONE_PADDING_METERS,
      width: 10 + ZONE_PADDING_METERS * 2,
      height: 20 + ZONE_PADDING_METERS * 2,
    });
  });

  it('빈 선택이면 null을 준다', () => {
    expect(boundingBoxOf([], [], [], 100, 100)).toBeNull();
  });

  it('도면 경계 밖으로 나가지 않게 잘라낸다', () => {
    const box = boundingBoxOf([wall(98, 98, 99, 99)], [], [], 100, 100);

    expect(box!.x + box!.width).toBeLessThanOrEqual(100);
    expect(box!.y + box!.height).toBeLessThanOrEqual(100);
  });
});
