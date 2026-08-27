import type { DrawingDocument, Fabric, Pillar, PointSelection, Vec2 } from '../types';
import { distance, distanceToSegment, rectCenter, rotatePoint } from './geometry';
import { hitTestRect } from './hitTest';

export const START_TOUCH_EPS = 0.1;
const GEOM_EPS = 1e-6;
const BOUNDARY_EPS = 0.01;
const PROBE_EPS = 1e-4;

interface RayHit {
  t: number;
  collinear: boolean;
}

export function isInsideObstacleRect(point: Vec2, doc: DrawingDocument): boolean {
  for (const pillar of doc.pillars) {
    if (hitTestRect(pillar, point, 0)) {
      return true;
    }
  }
  for (const fabric of doc.fabrics) {
    if (hitTestRect(fabric, point, 0)) {
      return true;
    }
  }
  return false;
}

export function clampLineDraft(start: Vec2, end: Vec2, doc: DrawingDocument): Vec2 {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const len = Math.hypot(dx, dy);
  if (len < GEOM_EPS) {
    return end;
  }
  const dir = { x: dx / len, y: dy / len };
  let minDist = len;
  for (const [a, b] of segmentObstacles(doc)) {
    const hit = raySegmentHitT(start, dir, a, b, true);
    if (hit === null) {
      continue;
    }
    if (hit.collinear) {
      const eA = (a.x - start.x) * dir.x + (a.y - start.y) * dir.y;
      const eB = (b.x - start.x) * dir.x + (b.y - start.y) * dir.y;
      const overlapStart = Math.max(0, Math.min(eA, eB));
      const overlapEnd = Math.min(len, Math.max(eA, eB));
      if (overlapEnd > overlapStart + GEOM_EPS && overlapStart < minDist) {
        minDist = overlapStart;
      }
    } else if (hit.t > START_TOUCH_EPS && hit.t < minDist) {
      minDist = hit.t;
    } else if (
      hit.t <= START_TOUCH_EPS &&
      distanceToSegment(start, a, b) > BOUNDARY_EPS &&
      hit.t < minDist
    ) {
      minDist = hit.t;
    }
  }
  for (const rect of rectObstacles(doc)) {
    const corners = rectCorners(rect);
    if (startBlockedByRect(rect, corners, start, dir)) {
      minDist = 0;
      break;
    }
    for (let i = 0; i < 4; i++) {
      const edgeA = corners[i];
      const edgeB = corners[(i + 1) % 4];
      const hit = raySegmentHitT(start, dir, edgeA, edgeB, false);
      if (hit === null) {
        continue;
      }
      if (hit.t > START_TOUCH_EPS && hit.t < minDist) {
        minDist = hit.t;
      } else if (
        hit.t <= START_TOUCH_EPS &&
        distanceToSegment(start, edgeA, edgeB) > BOUNDARY_EPS &&
        hit.t < minDist
      ) {
        minDist = hit.t;
      }
    }
  }
  if (minDist < len) {
    return { x: start.x + dir.x * minDist, y: start.y + dir.y * minDist };
  }
  return end;
}

export function clampRectDraft(start: Vec2, end: Vec2, doc: DrawingDocument): Vec2 {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  if (Math.abs(dx) < GEOM_EPS && Math.abs(dy) < GEOM_EPS) {
    return end;
  }
  if (Math.abs(dx) < GEOM_EPS || Math.abs(dy) < GEOM_EPS) {
    return clampLineDraft(start, end, doc);
  }
  const len = Math.hypot(dx, dy);
  const dir = { x: dx / len, y: dy / len };
  let minT = 1;
  for (const [a, b] of segmentObstacles(doc)) {
    const t = rectSegmentTouchT(start.x, start.y, dx, dy, a, b);
    if (t < minT) {
      minT = t;
    }
  }
  for (const rect of rectObstacles(doc)) {
    const corners = rectCorners(rect);
    if (startBlockedByRect(rect, corners, start, dir)) {
      minT = 0;
      break;
    }
    for (let i = 0; i < 4; i++) {
      const t = rectSegmentTouchT(start.x, start.y, dx, dy, corners[i], corners[(i + 1) % 4]);
      if (t < minT) {
        minT = t;
      }
    }
  }
  if (minT < 1) {
    return { x: start.x + dx * minT, y: start.y + dy * minT };
  }
  return end;
}

function segmentObstacles(doc: DrawingDocument): Array<[Vec2, Vec2]> {
  const result: Array<[Vec2, Vec2]> = [];
  for (const wall of doc.walls) {
    result.push([
      { x: wall.startX, y: wall.startY },
      { x: wall.endX, y: wall.endY },
    ]);
  }
  for (const wall of doc.outsideWalls) {
    result.push([
      { x: wall.startX, y: wall.startY },
      { x: wall.endX, y: wall.endY },
    ]);
  }
  return result;
}

function rectObstacles(doc: DrawingDocument): Array<Pillar | Fabric> {
  return [...doc.pillars, ...doc.fabrics];
}

export function rectCorners(element: Pillar | Fabric): Vec2[] {
  const center = rectCenter(element);
  return [
    rotatePoint({ x: element.startX, y: element.startY }, center, element.rotation),
    rotatePoint({ x: element.startX, y: element.endY }, center, element.rotation),
    rotatePoint({ x: element.endX, y: element.endY }, center, element.rotation),
    rotatePoint({ x: element.endX, y: element.startY }, center, element.rotation),
  ];
}

function startBlockedByRect(
  rect: Pillar | Fabric,
  corners: Vec2[],
  start: Vec2,
  dir: Vec2,
): boolean {
  let onBoundary = false;
  for (let i = 0; i < 4; i++) {
    if (distanceToSegment(start, corners[i], corners[(i + 1) % 4]) <= BOUNDARY_EPS) {
      onBoundary = true;
      break;
    }
  }
  if (!onBoundary) {
    return false;
  }
  const probe = { x: start.x + dir.x * PROBE_EPS, y: start.y + dir.y * PROBE_EPS };
  return hitTestRect(rect, probe, 0);
}

function rectSegmentTouchT(
  sx: number,
  sy: number,
  dx: number,
  dy: number,
  a: Vec2,
  b: Vec2,
): number {
  let best = Infinity;
  best = Math.min(best, pointEnterT(sx, sy, dx, dy, a), pointEnterT(sx, sy, dx, dy, b));
  const rays: Vec2[] = [];
  if (Math.abs(dx) > GEOM_EPS) {
    rays.push({ x: dx, y: 0 });
  }
  if (Math.abs(dy) > GEOM_EPS) {
    rays.push({ x: 0, y: dy });
  }
  if (Math.abs(dx) > GEOM_EPS && Math.abs(dy) > GEOM_EPS) {
    rays.push({ x: dx, y: dy });
  }
  for (const ray of rays) {
    const hit = raySegmentHitT({ x: sx, y: sy }, ray, a, b, false);
    if (hit === null) {
      continue;
    }
    if (hit.t > GEOM_EPS) {
      if (hit.t < best) {
        best = hit.t;
      }
    } else if (segmentEntersQuadrant({ x: sx, y: sy }, a, b, dx, dy)) {
      best = 0;
      break;
    }
  }
  return best;
}

function pointEnterT(sx: number, sy: number, dx: number, dy: number, point: Vec2): number {
  return Math.max(axisEnterT(sx, dx, point.x), axisEnterT(sy, dy, point.y));
}

function axisEnterT(origin: number, delta: number, value: number): number {
  if (delta > 0) {
    if (value <= origin) {
      return Infinity;
    }
    return (value - origin) / delta;
  }
  if (delta < 0) {
    if (value >= origin) {
      return Infinity;
    }
    return (origin - value) / -delta;
  }
  return Infinity;
}

function segmentEntersQuadrant(start: Vec2, a: Vec2, b: Vec2, dx: number, dy: number): boolean {
  const dStartA = distance(start, a);
  const dStartB = distance(start, b);
  if (dStartA < GEOM_EPS && dStartB < GEOM_EPS) {
    return false;
  }
  const inQ = (v: Vec2) => {
    if (Math.abs(v.x) < GEOM_EPS || Math.abs(v.y) < GEOM_EPS) {
      return false;
    }
    return Math.sign(v.x) === Math.sign(dx) && Math.sign(v.y) === Math.sign(dy);
  };
  if (dStartB > GEOM_EPS && inQ({ x: b.x - a.x, y: b.y - a.y })) {
    return true;
  }
  if (dStartA > GEOM_EPS && inQ({ x: a.x - b.x, y: a.y - b.y })) {
    return true;
  }
  return false;
}

export function clampMoveDelta(doc: DrawingDocument, selection: PointSelection, delta: Vec2): Vec2 {
  if (Math.hypot(delta.x, delta.y) < GEOM_EPS) {
    return delta;
  }
  const movedSegments: Array<[Vec2, Vec2]> = [];
  const movedRects: Array<Pillar | Fabric> = [];
  const obstacleSegments: Array<[Vec2, Vec2]> = [];
  const obstacleRects: Array<Pillar | Fabric> = [];
  for (const wall of doc.walls) {
    const pair: [Vec2, Vec2] = [
      { x: wall.startX, y: wall.startY },
      { x: wall.endX, y: wall.endY },
    ];
    if (selection.wallIds.includes(wall.id)) {
      movedSegments.push(pair);
    } else {
      obstacleSegments.push(pair);
    }
  }
  for (const wall of doc.outsideWalls) {
    const pair: [Vec2, Vec2] = [
      { x: wall.startX, y: wall.startY },
      { x: wall.endX, y: wall.endY },
    ];
    if (selection.outsideWallIds.includes(wall.id)) {
      movedSegments.push(pair);
    } else {
      obstacleSegments.push(pair);
    }
  }
  for (const pillar of doc.pillars) {
    if (selection.pillarIds.includes(pillar.id)) {
      movedRects.push(pillar);
    } else {
      obstacleRects.push(pillar);
    }
  }
  for (const fabric of doc.fabrics) {
    if (selection.fabricIds.includes(fabric.id)) {
      movedRects.push(fabric);
    } else {
      obstacleRects.push(fabric);
    }
  }
  if (movedSegments.length === 0 && movedRects.length === 0) {
    return delta;
  }
  if (obstacleSegments.length === 0 && obstacleRects.length === 0) {
    return delta;
  }
  const len = Math.hypot(delta.x, delta.y);
  const dir = { x: delta.x / len, y: delta.y / len };
  let minS = len;
  for (const [a, b] of movedSegments) {
    for (const [oa, ob] of obstacleSegments) {
      const s = segmentSegmentTouchS(a, b, oa, ob, dir);
      if (s !== null && s < minS) {
        minS = s;
      }
    }
    for (const rect of obstacleRects) {
      const s = segmentRectTouchS(a, b, rectCorners(rect), dir);
      if (s !== null && s < minS) {
        minS = s;
      }
    }
  }
  for (const rect of movedRects) {
    const corners = rectCorners(rect);
    for (const [oa, ob] of obstacleSegments) {
      const s = rectSegmentTouchS(corners, oa, ob, dir);
      if (s !== null && s < minS) {
        minS = s;
      }
    }
    for (const obstacle of obstacleRects) {
      const s = rectRectTouchS(corners, rectCorners(obstacle), dir);
      if (s !== null && s < minS) {
        minS = s;
      }
    }
  }
  if (minS < len) {
    const s = minS / len;
    return { x: delta.x * s, y: delta.y * s };
  }
  return delta;
}

function segmentSegmentTouchS(a: Vec2, b: Vec2, oa: Vec2, ob: Vec2, dir: Vec2): number | null {
  if (segmentsStrictlyIntersect(a, b, oa, ob, true)) {
    return null;
  }
  let best = Infinity;
  for (const p of [a, b]) {
    const s = rayHitS(p, dir, oa, ob, true);
    if (s !== null && s < best) {
      best = s;
    }
  }
  const back = { x: -dir.x, y: -dir.y };
  for (const p of [oa, ob]) {
    const s = rayHitS(p, back, a, b, true);
    if (s !== null && s < best) {
      best = s;
    }
  }
  return best === Infinity ? null : best;
}

function segmentRectTouchS(a: Vec2, b: Vec2, corners: Vec2[], dir: Vec2): number | null {
  if (segmentStrictlyOverlapsRect(a, b, corners)) {
    return null;
  }
  let best = Infinity;
  for (const p of [a, b]) {
    for (let i = 0; i < 4; i++) {
      const s = rayHitS(p, dir, corners[i], corners[(i + 1) % 4], false);
      if (s !== null && s < best) {
        best = s;
      }
    }
    if (onRectBoundary(corners, p) && pointStrictlyInRect(corners, probePoint(p, dir))) {
      return 0;
    }
  }
  const back = { x: -dir.x, y: -dir.y };
  for (let i = 0; i < 4; i++) {
    const s = rayHitS(corners[i], back, a, b, false);
    if (s !== null && s < best) {
      best = s;
    }
  }
  return best === Infinity ? null : best;
}

function rectSegmentTouchS(corners: Vec2[], oa: Vec2, ob: Vec2, dir: Vec2): number | null {
  if (segmentStrictlyOverlapsRect(oa, ob, corners)) {
    return null;
  }
  let best = Infinity;
  for (const corner of corners) {
    const s = rayHitS(corner, dir, oa, ob, false);
    if (s !== null && s < best) {
      best = s;
    }
  }
  const back = { x: -dir.x, y: -dir.y };
  for (const p of [oa, ob]) {
    for (let i = 0; i < 4; i++) {
      const s = rayHitS(p, back, corners[i], corners[(i + 1) % 4], false);
      if (s !== null && s < best) {
        best = s;
      }
    }
    if (onRectBoundary(corners, p) && pointStrictlyInRect(corners, probePoint(p, back))) {
      return 0;
    }
  }
  return best === Infinity ? null : best;
}

function rectRectTouchS(cornersA: Vec2[], cornersB: Vec2[], dir: Vec2): number | null {
  let first = 0;
  let allStrictAtZero = true;
  for (const axis of rectAxes(cornersA, cornersB)) {
    const [m0, m1] = projectRange(cornersA, axis);
    const [s0, s1] = projectRange(cornersB, axis);
    const d = dot(dir, axis);
    if (Math.abs(d) < GEOM_EPS) {
      if (!(m0 < s1 - GEOM_EPS && m1 > s0 + GEOM_EPS)) {
        return null;
      }
      continue;
    }
    const lo = d > 0 ? (s0 - m1) / d : (s1 - m0) / d;
    const hi = d > 0 ? (s1 - m0) / d : (s0 - m1) / d;
    if (hi <= GEOM_EPS || lo >= hi - GEOM_EPS) {
      return null;
    }
    if (lo < -GEOM_EPS) {
      continue;
    }
    allStrictAtZero = false;
    first = Math.max(first, Math.max(0, lo));
  }
  if (allStrictAtZero) {
    return null;
  }
  return first;
}

function rectAxes(cornersA: Vec2[], cornersB: Vec2[]): Vec2[] {
  const axes: Vec2[] = [];
  for (const corners of [cornersA, cornersB]) {
    for (let i = 0; i < 4; i++) {
      const e = {
        x: corners[(i + 1) % 4].x - corners[i].x,
        y: corners[(i + 1) % 4].y - corners[i].y,
      };
      const length = Math.hypot(e.x, e.y);
      if (length > GEOM_EPS) {
        axes.push({ x: -e.y / length, y: e.x / length });
      }
    }
  }
  return axes;
}

function projectRange(corners: Vec2[], axis: Vec2): [number, number] {
  let min = Infinity;
  let max = -Infinity;
  for (const corner of corners) {
    const p = corner.x * axis.x + corner.y * axis.y;
    min = Math.min(min, p);
    max = Math.max(max, p);
  }
  return [min, max];
}

function dot(a: Vec2, b: Vec2): number {
  return a.x * b.x + a.y * b.y;
}

function onRectBoundary(corners: Vec2[], point: Vec2): boolean {
  for (let i = 0; i < 4; i++) {
    if (distanceToSegment(point, corners[i], corners[(i + 1) % 4]) <= BOUNDARY_EPS) {
      return true;
    }
  }
  return false;
}

function probePoint(point: Vec2, dir: Vec2): Vec2 {
  return { x: point.x + dir.x * PROBE_EPS, y: point.y + dir.y * PROBE_EPS };
}

function rayHitS(
  origin: Vec2,
  dir: Vec2,
  a: Vec2,
  b: Vec2,
  includeCollinear: boolean,
): number | null {
  const hit = raySegmentHitT(origin, dir, a, b, includeCollinear);
  if (hit === null) {
    return null;
  }
  if (hit.collinear) {
    const eA = (a.x - origin.x) * dir.x + (a.y - origin.y) * dir.y;
    const eB = (b.x - origin.x) * dir.x + (b.y - origin.y) * dir.y;
    const overlapStart = Math.max(Math.min(eA, eB), 0);
    const overlapEnd = Math.max(eA, eB);
    if (overlapEnd > overlapStart + GEOM_EPS) {
      return overlapStart;
    }
    return null;
  }
  if (hit.t <= GEOM_EPS) {
    return null;
  }
  return hit.t;
}

export function clampRotate(
  element: {
    startX: number;
    startY: number;
    endX: number;
    endY: number;
    rotation: number;
    id: string;
  },
  candidateRotation: number,
  doc: DrawingDocument,
): number {
  const center = rectCenter(element);
  const localCorners: Vec2[] = [
    { x: element.startX - center.x, y: element.startY - center.y },
    { x: element.startX - center.x, y: element.endY - center.y },
    { x: element.endX - center.x, y: element.endY - center.y },
    { x: element.endX - center.x, y: element.startY - center.y },
  ];
  const obstacles = rotationObstacles(doc, element.id);
  if (obstacles.segments.length === 0 && obstacles.rects.length === 0) {
    return candidateRotation;
  }
  const r0 = (element.rotation * Math.PI) / 180;
  const r1 = (candidateRotation * Math.PI) / 180;
  const sweep = wrapAngle(r1 - r0);
  if (Math.abs(sweep) < GEOM_EPS) {
    return candidateRotation;
  }
  const currentCorners = localCorners.map((u) => add(center, rotateOffset(u, r0)));
  if (overlapsAny(currentCorners, obstacles)) {
    return candidateRotation;
  }
  const probeDelta = Math.sign(sweep) * PROBE_EPS;
  if (
    overlapsAny(
      localCorners.map((u) => add(center, rotateOffset(u, r0 + probeDelta))),
      obstacles,
    )
  ) {
    return element.rotation;
  }
  let bestDelta = Math.abs(sweep);
  for (const [a, b] of obstacles.segments) {
    for (const u of localCorners) {
      const delta = cornerArcDelta(center, u, a, b, r0, sweep);
      if (delta !== null && Math.abs(delta) < bestDelta) {
        bestDelta = Math.abs(delta);
      }
    }
    for (const p of [a, b]) {
      for (let i = 0; i < 4; i++) {
        const delta = vertexEdgeDelta(
          p,
          localCorners[i],
          localCorners[(i + 1) % 4],
          center,
          r0,
          sweep,
        );
        if (delta !== null && Math.abs(delta) < bestDelta) {
          bestDelta = Math.abs(delta);
        }
      }
    }
  }
  for (const rect of obstacles.rects) {
    const corners = rectCorners(rect);
    for (const u of localCorners) {
      for (let i = 0; i < 4; i++) {
        const delta = cornerArcDelta(center, u, corners[i], corners[(i + 1) % 4], r0, sweep);
        if (delta !== null && Math.abs(delta) < bestDelta) {
          bestDelta = Math.abs(delta);
        }
      }
    }
    for (let j = 0; j < 4; j++) {
      for (let i = 0; i < 4; i++) {
        const delta = vertexEdgeDelta(
          corners[j],
          localCorners[i],
          localCorners[(i + 1) % 4],
          center,
          r0,
          sweep,
        );
        if (delta !== null && Math.abs(delta) < bestDelta) {
          bestDelta = Math.abs(delta);
        }
      }
    }
  }
  if (bestDelta >= Math.abs(sweep)) {
    return candidateRotation;
  }
  const clamped = normalizeDegrees(((r0 + Math.sign(sweep) * bestDelta) * 180) / Math.PI);
  return Math.round(clamped * 10) / 10;
}

interface RotationObstacles {
  segments: Array<[Vec2, Vec2]>;
  rects: Array<Pillar | Fabric>;
}

function rotationObstacles(doc: DrawingDocument, elementId: string): RotationObstacles {
  const segments: Array<[Vec2, Vec2]> = [];
  const rects: Array<Pillar | Fabric> = [];
  for (const wall of doc.walls) {
    if (wall.id !== elementId) {
      segments.push([
        { x: wall.startX, y: wall.startY },
        { x: wall.endX, y: wall.endY },
      ]);
    }
  }
  for (const wall of doc.outsideWalls) {
    if (wall.id !== elementId) {
      segments.push([
        { x: wall.startX, y: wall.startY },
        { x: wall.endX, y: wall.endY },
      ]);
    }
  }
  for (const pillar of doc.pillars) {
    if (pillar.id !== elementId) {
      rects.push(pillar);
    }
  }
  for (const fabric of doc.fabrics) {
    if (fabric.id !== elementId) {
      rects.push(fabric);
    }
  }
  return { segments, rects };
}

function overlapsAny(corners: Vec2[], obstacles: RotationObstacles): boolean {
  for (const [a, b] of obstacles.segments) {
    if (segmentStrictlyOverlapsRect(a, b, corners)) {
      return true;
    }
  }
  for (const rect of obstacles.rects) {
    if (rectsStrictlyOverlap(corners, rectCorners(rect))) {
      return true;
    }
  }
  return false;
}

function cornerArcDelta(
  center: Vec2,
  u: Vec2,
  a: Vec2,
  b: Vec2,
  r0: number,
  sweep: number,
): number | null {
  const radius = Math.hypot(u.x, u.y);
  if (radius < GEOM_EPS) {
    return null;
  }
  const alpha0 = Math.atan2(u.y, u.x);
  let best: number | null = null;
  for (const alpha of circleSegmentAngles(center, radius, a, b)) {
    const delta = wrapAngle(alpha - alpha0 - r0);
    if (Math.sign(delta) === Math.sign(sweep) && Math.abs(delta) <= Math.abs(sweep) + GEOM_EPS) {
      if (best === null || Math.abs(delta) < Math.abs(best)) {
        best = delta;
      }
    }
  }
  return best;
}

function vertexEdgeDelta(
  p: Vec2,
  u1: Vec2,
  u2: Vec2,
  center: Vec2,
  r0: number,
  sweep: number,
): number | null {
  const px = p.x - center.x;
  const py = p.y - center.y;
  const vx = u2.x - u1.x;
  const vy = u2.y - u1.y;
  const lenSq = vx * vx + vy * vy;
  if (lenSq < GEOM_EPS) {
    return null;
  }
  const a = px * vy - py * vx;
  const b = px * vx + py * vy;
  const d = u1.x * vy - u1.y * vx;
  const r = Math.hypot(a, b);
  if (r < GEOM_EPS) {
    return null;
  }
  if (Math.abs(d) > r) {
    return null;
  }
  const ratio = d / r;
  const phi = Math.atan2(b, a);
  const acosValue = Math.acos(ratio);
  let best: number | null = null;
  for (const base of [phi + acosValue, phi - acosValue]) {
    for (let k = -1; k <= 1; k++) {
      const angle = base + k * 2 * Math.PI;
      const delta = wrapAngle(angle - r0);
      if (Math.sign(delta) !== Math.sign(sweep) || Math.abs(delta) > Math.abs(sweep) + GEOM_EPS) {
        continue;
      }
      const rotX = px * Math.cos(angle) + py * Math.sin(angle);
      const rotY = -px * Math.sin(angle) + py * Math.cos(angle);
      const s = ((rotX - u1.x) * vx + (rotY - u1.y) * vy) / lenSq;
      if (s < -GEOM_EPS || s > 1 + GEOM_EPS) {
        continue;
      }
      if (best === null || Math.abs(delta) < Math.abs(best)) {
        best = delta;
      }
    }
  }
  return best;
}

function circleSegmentAngles(center: Vec2, radius: number, a: Vec2, b: Vec2): number[] {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const lenSq = dx * dx + dy * dy;
  if (lenSq < GEOM_EPS) {
    return [];
  }
  const len = Math.sqrt(lenSq);
  const ux = dx / len;
  const uy = dy / len;
  const wx = center.x - a.x;
  const wy = center.y - a.y;
  const proj = wx * ux + wy * uy;
  const perp = wx * uy - wy * ux;
  if (Math.abs(perp) > radius + GEOM_EPS) {
    return [];
  }
  const h = Math.sqrt(Math.max(0, radius * radius - perp * perp));
  const qx = a.x + ux * proj;
  const qy = a.y + uy * proj;
  const angles: number[] = [];
  for (const sign of [1, -1]) {
    const px = qx + sign * h * ux;
    const py = qy + sign * h * uy;
    const t = ((px - a.x) * dx + (py - a.y) * dy) / lenSq;
    if (t >= -GEOM_EPS && t <= 1 + GEOM_EPS) {
      angles.push(normalizeAngle(Math.atan2(py - center.y, px - center.x)));
    }
  }
  return angles;
}

function rotateOffset(u: Vec2, angle: number): Vec2 {
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);
  return { x: u.x * cos - u.y * sin, y: u.x * sin + u.y * cos };
}

function add(a: Vec2, b: Vec2): Vec2 {
  return { x: a.x + b.x, y: a.y + b.y };
}

function rectsStrictlyOverlap(cornersA: Vec2[], cornersB: Vec2[]): boolean {
  for (const axis of rectAxes(cornersA, cornersB)) {
    const [m0, m1] = projectRange(cornersA, axis);
    const [s0, s1] = projectRange(cornersB, axis);
    if (!(m0 < s1 - GEOM_EPS && m1 > s0 + GEOM_EPS)) {
      return false;
    }
  }
  return true;
}

function normalizeAngle(angle: number): number {
  const wrapped = angle % (2 * Math.PI);
  return wrapped < 0 ? wrapped + 2 * Math.PI : wrapped;
}

function wrapAngle(angle: number): number {
  const wrapped = ((angle + Math.PI) % (2 * Math.PI)) - Math.PI;
  return wrapped < -Math.PI ? wrapped + 2 * Math.PI : wrapped;
}

function normalizeDegrees(degrees: number): number {
  const wrapped = degrees % 360;
  return wrapped < 0 ? wrapped + 360 : wrapped;
}

function cross(a: Vec2, b: Vec2, c: Vec2): number {
  return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
}

function projectionParam(point: Vec2, a: Vec2, b: Vec2): number {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const lenSq = dx * dx + dy * dy;
  if (lenSq === 0) {
    return 0;
  }
  return ((point.x - a.x) * dx + (point.y - a.y) * dy) / lenSq;
}

function segmentsStrictlyIntersect(
  a1: Vec2,
  a2: Vec2,
  b1: Vec2,
  b2: Vec2,
  includeCollinear: boolean,
): boolean {
  const d1 = cross(a1, a2, b1);
  const d2 = cross(a1, a2, b2);
  const d3 = cross(b1, b2, a1);
  const d4 = cross(b1, b2, a2);
  const properCrossing =
    ((d1 > GEOM_EPS && d2 < -GEOM_EPS) || (d1 < -GEOM_EPS && d2 > GEOM_EPS)) &&
    ((d3 > GEOM_EPS && d4 < -GEOM_EPS) || (d3 < -GEOM_EPS && d4 > GEOM_EPS));
  if (properCrossing) {
    return true;
  }
  if (!includeCollinear) {
    return false;
  }
  if (Math.abs(d1) <= GEOM_EPS && Math.abs(d2) <= GEOM_EPS) {
    const t1 = projectionParam(b1, a1, a2);
    const t2 = projectionParam(b2, a1, a2);
    const lo = Math.max(0, Math.min(t1, t2));
    const hi = Math.min(1, Math.max(t1, t2));
    return hi - lo > GEOM_EPS;
  }
  return false;
}

function pointStrictlyInRect(corners: Vec2[], point: Vec2): boolean {
  let prev = 0;
  for (let i = 0; i < 4; i++) {
    const c = cross(corners[i], corners[(i + 1) % 4], point);
    if (Math.abs(c) <= GEOM_EPS) {
      return false;
    }
    if (prev !== 0 && c > 0 !== prev > 0) {
      return false;
    }
    prev = c;
  }
  return true;
}

function segmentStrictlyOverlapsRect(a: Vec2, b: Vec2, corners: Vec2[]): boolean {
  if (pointStrictlyInRect(corners, a) || pointStrictlyInRect(corners, b)) {
    return true;
  }
  for (let i = 0; i < 4; i++) {
    if (segmentsStrictlyIntersect(a, b, corners[i], corners[(i + 1) % 4], false)) {
      return true;
    }
  }
  return false;
}

function raySegmentHitT(
  origin: Vec2,
  dir: Vec2,
  a: Vec2,
  b: Vec2,
  includeCollinear: boolean,
): RayHit | null {
  const ex = b.x - a.x;
  const ey = b.y - a.y;
  const denom = dir.x * ey - dir.y * ex;
  const ox = a.x - origin.x;
  const oy = a.y - origin.y;
  if (Math.abs(denom) < 1e-12) {
    if (!includeCollinear) {
      return null;
    }
    if (Math.abs(dir.x * oy - dir.y * ox) > 1e-6) {
      return null;
    }
    const ta = dir.x * ox + dir.y * oy;
    const tb = dir.x * (b.x - origin.x) + dir.y * (b.y - origin.y);
    const lo = Math.min(ta, tb);
    const hi = Math.max(ta, tb);
    const first = Math.max(lo, 0);
    if (first <= hi + 1e-9) {
      return { t: first, collinear: true };
    }
    return null;
  }
  const t = (ox * ey - oy * ex) / denom;
  const s = (ox * dir.y - oy * dir.x) / denom;
  if (t >= -1e-9 && s >= -1e-9 && s <= 1 + 1e-9) {
    return { t: Math.max(t, 0), collinear: false };
  }
  return null;
}
