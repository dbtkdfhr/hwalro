import type { LayoutZone, ZoneRect } from '../api/layoutMetadataApi';
import type { Fabric, Pillar, Wall } from '../types';
import { rectCorners } from './collision';
import type { RectLike } from './hitTest';

export const ZONE_PADDING_METERS = 0.5;

export function zoneAsRect(zone: LayoutZone): RectLike {
  return {
    startX: zone.rect.x,
    startY: zone.rect.y,
    endX: zone.rect.x + zone.rect.width,
    endY: zone.rect.y + zone.rect.height,
    rotation: 0,
  };
}

/** 선택한 벽·기둥·구조물을 모두 감싸는 사각형. 회전을 반영한 AABB다. 패딩을 더하고 도면 경계로 잘라낸다. */
export function boundingBoxOf(
  walls: Wall[],
  pillars: Pillar[],
  fabrics: Fabric[],
  docWidth: number,
  docHeight: number,
): ZoneRect | null {
  const points: Array<{ x: number; y: number }> = [
    ...walls.flatMap((wall) => [
      { x: wall.startX, y: wall.startY },
      { x: wall.endX, y: wall.endY },
    ]),
    ...pillars.flatMap(rectCorners),
    ...fabrics.flatMap(rectCorners),
  ];
  if (points.length === 0) {
    return null;
  }
  const minX = Math.min(...points.map((point) => point.x)) - ZONE_PADDING_METERS;
  const minY = Math.min(...points.map((point) => point.y)) - ZONE_PADDING_METERS;
  const maxX = Math.max(...points.map((point) => point.x)) + ZONE_PADDING_METERS;
  const maxY = Math.max(...points.map((point) => point.y)) + ZONE_PADDING_METERS;
  const x = Math.max(0, minX);
  const y = Math.max(0, minY);
  return {
    x,
    y,
    width: Math.min(docWidth, maxX) - x,
    height: Math.min(docHeight, maxY) - y,
  };
}
