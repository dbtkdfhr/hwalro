import type { DrawingDocument, Fabric, OutsideWall, Pillar, Vec2, Wall } from '../types';
import { closestPointOnSegment, distance, PX_PER_METER, rectCenter, rotatePoint } from './geometry';

export const ANGLE_SNAP_DEG = 4;
export const ENDPOINT_MAGNET_PX = 10;

export interface SnapResult {
  point: Vec2;
  snappedToEndpoint: Vec2 | null;
  axisSnapped: boolean;
}

export interface SnapSources {
  walls: Wall[];
  outsideWalls: OutsideWall[];
  pillars: Pillar[];
  fabrics: Fabric[];
}

export function docSnapSources(doc: DrawingDocument): SnapSources {
  return {
    walls: doc.walls,
    outsideWalls: doc.outsideWalls,
    pillars: doc.pillars,
    fabrics: doc.fabrics,
  };
}

export function wallEndpoints(wall: Wall): [Vec2, Vec2] {
  return [
    { x: wall.startX, y: wall.startY },
    { x: wall.endX, y: wall.endY },
  ];
}

export function allEndpoints(sources: SnapSources): Vec2[] {
  const points: Vec2[] = [];
  for (const wall of sources.walls) {
    points.push({ x: wall.startX, y: wall.startY });
    points.push({ x: wall.endX, y: wall.endY });
  }
  for (const wall of sources.outsideWalls) {
    points.push({ x: wall.startX, y: wall.startY });
    points.push({ x: wall.endX, y: wall.endY });
  }
  for (const pillar of sources.pillars) {
    const center = rectCenter(pillar);
    points.push(rotatePoint({ x: pillar.startX, y: pillar.startY }, center, pillar.rotation));
    points.push(rotatePoint({ x: pillar.endX, y: pillar.endY }, center, pillar.rotation));
  }
  for (const fabric of sources.fabrics) {
    const center = rectCenter(fabric);
    points.push(rotatePoint({ x: fabric.startX, y: fabric.startY }, center, fabric.rotation));
    points.push(rotatePoint({ x: fabric.endX, y: fabric.endY }, center, fabric.rotation));
  }
  return points;
}

export function axisSnap(origin: Vec2, target: Vec2): Vec2 | null {
  const dx = target.x - origin.x;
  const dy = target.y - origin.y;
  if (Math.abs(dx) < 1e-6 && Math.abs(dy) < 1e-6) {
    return null;
  }
  const angle = (Math.atan2(dy, dx) * 180) / Math.PI;
  const folded = ((angle % 180) + 180) % 180;
  const distanceToHorizontal = Math.min(folded, 180 - folded);
  const distanceToVertical = 90 - distanceToHorizontal;
  if (Math.min(distanceToHorizontal, distanceToVertical) > ANGLE_SNAP_DEG) {
    return null;
  }
  if (distanceToHorizontal <= distanceToVertical) {
    return { x: target.x, y: origin.y };
  }
  return { x: origin.x, y: target.y };
}

export function endpointMagnet(
  point: Vec2,
  candidates: Vec2[],
  exclude: Vec2[],
  zoom: number,
): Vec2 | null {
  const radius = ENDPOINT_MAGNET_PX / (zoom * PX_PER_METER);
  let best: Vec2 | null = null;
  let bestDist = radius;
  for (const candidate of candidates) {
    if (exclude.some((e) => e.x === candidate.x && e.y === candidate.y)) {
      continue;
    }
    const dist = Math.hypot(candidate.x - point.x, candidate.y - point.y);
    if (dist <= bestDist) {
      bestDist = dist;
      best = candidate;
    }
  }
  return best;
}

export function surfaceSnap(
  point: Vec2,
  sources: SnapSources,
  zoom: number,
  exclude: Vec2[] = [],
): Vec2 | null {
  const radius = ENDPOINT_MAGNET_PX / (zoom * PX_PER_METER);
  let best: Vec2 | null = null;
  let bestDist = radius;
  const consider = (wall: Wall | OutsideWall) => {
    const a = { x: wall.startX, y: wall.startY };
    const b = { x: wall.endX, y: wall.endY };
    const startExcluded = exclude.some((e) => e.x === a.x && e.y === a.y);
    const endExcluded = exclude.some((e) => e.x === b.x && e.y === b.y);
    if (startExcluded && endExcluded) {
      return;
    }
    const closest = closestPointOnSegment(point, a, b);
    const dist = distance(point, closest);
    if (dist < bestDist) {
      bestDist = dist;
      best = closest;
    }
  };
  for (const wall of sources.walls) {
    consider(wall);
  }
  for (const wall of sources.outsideWalls) {
    consider(wall);
  }
  return best;
}

export function snapPoint(
  raw: Vec2,
  origin: Vec2,
  sources: SnapSources,
  exclude: Vec2[],
  zoom: number,
  enableAxisSnap = true,
): SnapResult {
  const magnet = endpointMagnet(raw, allEndpoints(sources), exclude, zoom);
  if (magnet) {
    return { point: magnet, snappedToEndpoint: magnet, axisSnapped: false };
  }
  const surface = surfaceSnap(raw, sources, zoom, exclude);
  if (surface) {
    return { point: surface, snappedToEndpoint: surface, axisSnapped: false };
  }
  if (enableAxisSnap) {
    const axis = axisSnap(origin, raw);
    if (axis) {
      return { point: axis, snappedToEndpoint: null, axisSnapped: true };
    }
  }
  return { point: raw, snappedToEndpoint: null, axisSnapped: false };
}
