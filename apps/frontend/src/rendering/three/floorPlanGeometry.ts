/**
 * 도면 한 층을 3D로 세울 때 쓰는 순수 기하 계산.
 *
 * <p>좌표 변환, 외곽선 조립, 비상구 투영과 에스컬레이터 배치처럼 three.js 객체를 만들지 않는 계산만 모았다. 순수 함수라
 * 테스트하기 쉽고, 시뮬레이션 결과 화면과 도면 화면이 같은 계산을 공유한다.
 *
 * <p>타입은 {@link ./floorPlanScene}의 계약을 쓴다. 여기서 시뮬레이션 타입을 참조하면 도면 화면도 시뮬레이션을 알아야
 * 한다.
 */
import type { PlanPoint, SceneExit, SceneRect, SceneSegment } from './floorPlanScene';

export type { PlanPoint };

/** 장면 좌표계의 점(m). 가운데가 원점이고 도면의 y가 z가 된다. */
export interface ScenePoint {
  x: number;
  z: number;
}

export interface EscalatorPlacement extends ScenePoint {
  length: number;
  width: number;
  rotationY: number;
}

export interface ExitPortalPlacement extends ScenePoint {
  width: number;
  rotationY: number;
  postWidth: number;
  postOffset: number;
  openingWidth: number;
}

export function worldToScene(
  x: number,
  y: number,
  worldWidth: number,
  worldHeight: number,
): ScenePoint {
  return { x: x - worldWidth / 2, z: y - worldHeight / 2 };
}

export function segmentTransform(segment: SceneSegment, worldWidth: number, worldHeight: number) {
  const start = worldToScene(segment.startX, segment.startY, worldWidth, worldHeight);
  const end = worldToScene(segment.endX, segment.endY, worldWidth, worldHeight);
  return {
    x: (start.x + end.x) / 2,
    z: (start.z + end.z) / 2,
    length: Math.hypot(end.x - start.x, end.z - start.z),
    rotationY: -Math.atan2(end.z - start.z, end.x - start.x),
  };
}

export function exitPortalPlacement(
  exit: SceneSegment,
  worldWidth: number,
  worldHeight: number,
): ExitPortalPlacement {
  const transform = segmentTransform(exit, worldWidth, worldHeight);
  const width = Math.max(0.4, transform.length);
  const postWidth = Math.min(0.28, Math.max(0.14, width * 0.12));
  const postOffset = Math.max(0, width / 2 - postWidth / 2);

  return {
    x: transform.x,
    z: transform.z,
    width,
    rotationY: transform.rotationY,
    postWidth,
    postOffset,
    openingWidth: Math.max(0.12, width - postWidth * 2),
  };
}

export function isEscalatorLabel(text: string) {
  return text.toUpperCase().replace(/[\s/]/g, '') === 'ES';
}

export function drawingRectContainsPoint(rectangle: SceneRect, point: PlanPoint) {
  const centerX = (rectangle.startX + rectangle.endX) / 2;
  const centerY = (rectangle.startY + rectangle.endY) / 2;
  const halfWidth = Math.abs(rectangle.endX - rectangle.startX) / 2;
  const halfHeight = Math.abs(rectangle.endY - rectangle.startY) / 2;
  const angle = -((rectangle.rotation ?? 0) * Math.PI) / 180;
  const deltaX = point.x - centerX;
  const deltaY = point.y - centerY;
  const localX = deltaX * Math.cos(angle) - deltaY * Math.sin(angle);
  const localY = deltaX * Math.sin(angle) + deltaY * Math.cos(angle);
  const tolerance = 0.001;
  return Math.abs(localX) <= halfWidth + tolerance && Math.abs(localY) <= halfHeight + tolerance;
}

export function escalatorPlacementFromRect(
  rectangle: SceneRect,
  worldWidth: number,
  worldHeight: number,
): EscalatorPlacement {
  const rectangleWidth = Math.max(0.2, Math.abs(rectangle.endX - rectangle.startX));
  const rectangleHeight = Math.max(0.2, Math.abs(rectangle.endY - rectangle.startY));
  const center = worldToScene(
    (rectangle.startX + rectangle.endX) / 2,
    (rectangle.startY + rectangle.endY) / 2,
    worldWidth,
    worldHeight,
  );
  return {
    ...center,
    length: Math.max(rectangleWidth, rectangleHeight),
    width: Math.min(rectangleWidth, rectangleHeight),
    rotationY:
      -((rectangle.rotation ?? 0) * Math.PI) / 180 -
      (rectangleHeight > rectangleWidth ? Math.PI / 2 : 0),
  };
}

export function escalatorRunElevation(progress: number, direction: 1 | -1, rise: number) {
  const clampedProgress = Math.min(1, Math.max(0, progress));
  return rise * (direction === 1 ? clampedProgress : 1 - clampedProgress);
}

export function createBoundarySegments(points: PlanPoint[]): SceneSegment[] {
  if (points.length < 2) return [];
  return points.map((point, index) => {
    const next = points[(index + 1) % points.length];
    return {
      name: `outside-wall-${index}`,
      startX: point.x,
      startY: point.y,
      endX: next.x,
      endY: next.y,
    };
  });
}

function pointDistanceToLine(point: PlanPoint, segment: SceneSegment) {
  const deltaX = segment.endX - segment.startX;
  const deltaY = segment.endY - segment.startY;
  const length = Math.hypot(deltaX, deltaY);
  if (length === 0) return Number.POSITIVE_INFINITY;
  return (
    Math.abs(
      deltaY * point.x -
        deltaX * point.y +
        segment.endX * segment.startY -
        segment.endY * segment.startX,
    ) / length
  );
}

function isParallelToSegment(first: SceneSegment, second: SceneSegment) {
  const firstX = first.endX - first.startX;
  const firstY = first.endY - first.startY;
  const secondX = second.endX - second.startX;
  const secondY = second.endY - second.startY;
  const firstLength = Math.hypot(firstX, firstY);
  const secondLength = Math.hypot(secondX, secondY);
  if (firstLength === 0 || secondLength === 0) return false;
  return Math.abs((firstX * secondX + firstY * secondY) / (firstLength * secondLength)) >= 0.9;
}

function segmentProjectionOverlapLength(exit: SceneSegment, segment: SceneSegment) {
  const deltaX = segment.endX - segment.startX;
  const deltaY = segment.endY - segment.startY;
  const lengthSquared = deltaX * deltaX + deltaY * deltaY;
  if (lengthSquared === 0) return 0;
  const project = (x: number, y: number) =>
    ((x - segment.startX) * deltaX + (y - segment.startY) * deltaY) / lengthSquared;
  const start = Math.min(project(exit.startX, exit.startY), project(exit.endX, exit.endY));
  const end = Math.max(project(exit.startX, exit.startY), project(exit.endX, exit.endY));
  return Math.max(0, Math.min(1, end) - Math.max(0, start)) * Math.sqrt(lengthSquared);
}

const MAX_BOUNDARY_EXIT_OFFSET = 2;

function findNearestBoundarySegment(segments: SceneSegment[], exit: SceneSegment) {
  const midpoint = {
    x: (exit.startX + exit.endX) / 2,
    y: (exit.startY + exit.endY) / 2,
  };
  const nearestCandidate = segments
    .filter((segment) => isParallelToSegment(exit, segment))
    .map((segment) => ({
      segment,
      distance: pointDistanceToLine(midpoint, segment),
      overlapLength: segmentProjectionOverlapLength(exit, segment),
    }))
    .filter(
      (candidate) =>
        candidate.overlapLength > 0.001 && candidate.distance <= MAX_BOUNDARY_EXIT_OFFSET,
    )
    .sort((first, second) => {
      const overlapDifference = second.overlapLength - first.overlapLength;
      if (Math.abs(overlapDifference) > 0.000001) return overlapDifference;
      return first.distance - second.distance;
    })[0];
  return nearestCandidate?.segment ?? null;
}

function projectPointToSegmentLine(point: PlanPoint, segment: SceneSegment) {
  const deltaX = segment.endX - segment.startX;
  const deltaY = segment.endY - segment.startY;
  const lengthSquared = deltaX * deltaX + deltaY * deltaY;
  if (lengthSquared === 0) return point;
  const ratio =
    ((point.x - segment.startX) * deltaX + (point.y - segment.startY) * deltaY) / lengthSquared;
  return {
    x: segment.startX + deltaX * ratio,
    y: segment.startY + deltaY * ratio,
  };
}

export function projectExitsToBoundarySegments<T extends SceneSegment>(
  segments: SceneSegment[],
  exits: T[],
): T[] {
  return exits.map((exit) => {
    const segment = findNearestBoundarySegment(segments, exit);
    if (!segment) return exit;
    const start = projectPointToSegmentLine({ x: exit.startX, y: exit.startY }, segment);
    const end = projectPointToSegmentLine({ x: exit.endX, y: exit.endY }, segment);
    return {
      ...exit,
      startX: start.x,
      startY: start.y,
      endX: end.x,
      endY: end.y,
    };
  });
}

export function splitBoundarySegmentsAtExits(segments: SceneSegment[], exits: SceneSegment[]) {
  const exitsBySegment = new Map<SceneSegment, SceneSegment[]>();

  for (const exit of projectExitsToBoundarySegments(segments, exits)) {
    const nearestSegment = findNearestBoundarySegment(segments, exit);
    if (!nearestSegment) continue;
    const assignedExits = exitsBySegment.get(nearestSegment) ?? [];
    assignedExits.push(exit);
    exitsBySegment.set(nearestSegment, assignedExits);
  }

  return segments.flatMap((segment) =>
    splitBoundarySegmentAtExits(
      segment,
      exitsBySegment.get(segment) ?? [],
      MAX_BOUNDARY_EXIT_OFFSET,
    ),
  );
}

export function splitBoundarySegmentsAtActiveExits(segments: SceneSegment[], exits: SceneExit[]) {
  return splitBoundarySegmentsAtExits(
    segments,
    exits.filter((exit) => exit.active),
  );
}

export function splitBoundarySegmentAtExits(
  segment: SceneSegment,
  exits: SceneSegment[],
  tolerance = 0.45,
): SceneSegment[] {
  const deltaX = segment.endX - segment.startX;
  const deltaY = segment.endY - segment.startY;
  const lengthSquared = deltaX * deltaX + deltaY * deltaY;
  if (lengthSquared === 0) return [];

  const openingRanges = exits
    .filter(
      (exit) =>
        pointDistanceToLine({ x: exit.startX, y: exit.startY }, segment) <= tolerance &&
        pointDistanceToLine({ x: exit.endX, y: exit.endY }, segment) <= tolerance,
    )
    .map((exit) => {
      const project = (x: number, y: number) =>
        ((x - segment.startX) * deltaX + (y - segment.startY) * deltaY) / lengthSquared;
      return [
        Math.max(0, Math.min(project(exit.startX, exit.startY), project(exit.endX, exit.endY))),
        Math.min(1, Math.max(project(exit.startX, exit.startY), project(exit.endX, exit.endY))),
      ] as const;
    })
    .filter(([start, end]) => end - start > 0.001)
    .sort(([firstStart], [secondStart]) => firstStart - secondStart);

  const mergedRanges: Array<[number, number]> = [];
  for (const [start, end] of openingRanges) {
    const previous = mergedRanges[mergedRanges.length - 1];
    if (previous && start <= previous[1]) previous[1] = Math.max(previous[1], end);
    else mergedRanges.push([start, end]);
  }

  const wallRanges: Array<[number, number]> = [];
  let cursor = 0;
  for (const [start, end] of mergedRanges) {
    if (start - cursor > 0.001) wallRanges.push([cursor, start]);
    cursor = Math.max(cursor, end);
  }
  if (1 - cursor > 0.001) wallRanges.push([cursor, 1]);

  return wallRanges.map(([start, end], index) => ({
    name: `${segment.name}-part-${index}`,
    startX: segment.startX + deltaX * start,
    startY: segment.startY + deltaY * start,
    endX: segment.startX + deltaX * end,
    endY: segment.startY + deltaY * end,
  }));
}
