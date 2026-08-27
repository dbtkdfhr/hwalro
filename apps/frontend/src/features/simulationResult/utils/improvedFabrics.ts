import type { DrawingRect } from '../types';

const GEOMETRY_EPSILON = 1e-6;

function sameGeometry(left: DrawingRect, right: DrawingRect) {
  return (
    Math.abs(left.startX - right.startX) < GEOMETRY_EPSILON &&
    Math.abs(left.startY - right.startY) < GEOMETRY_EPSILON &&
    Math.abs(left.endX - right.endX) < GEOMETRY_EPSILON &&
    Math.abs(left.endY - right.endY) < GEOMETRY_EPSILON &&
    Math.abs((left.rotation ?? 0) - (right.rotation ?? 0)) < GEOMETRY_EPSILON
  );
}

export interface ImprovedFabricDiff {
  /** 기존 배치에서 이동 전 자리였던 구조물 인덱스 */
  originIndexes: number[];
  /** 개선 배치에서 이동했거나 새로 추가된 구조물 인덱스 */
  improvedIndexes: number[];
}

/**
 * 두 배치 도면의 구조물(fabric)을 비교해 어느 쪽 화면에서 강조할지 인덱스를 구한다.
 * 채택 버전은 원본 구조물을 이름을 유지한 채 복사하고 MOVE_FABRIC으로 일부만 옮기므로,
 * 같은 이름끼리 짝지어 변하지 않은 것부터 소거하면 나머지가 개선 대상이다.
 */
export function findImprovedFabricDiff(
  originFabrics: DrawingRect[],
  currentFabrics: DrawingRect[],
): ImprovedFabricDiff {
  const originGroups = new Map<string, Array<{ fabric: DrawingRect; index: number }>>();
  originFabrics.forEach((fabric, index) => {
    const group = originGroups.get(fabric.name);
    if (group) group.push({ fabric, index });
    else originGroups.set(fabric.name, [{ fabric, index }]);
  });

  const consumedOrigin = new Set<number>();
  const movedOrigin = new Set<number>();
  const changedCurrent = new Set<number>();

  const takeUnconsumed = (name: string, match?: (origin: DrawingRect) => boolean) => {
    const group = originGroups.get(name);
    const entry = group?.find(
      (candidate) => !consumedOrigin.has(candidate.index) && (!match || match(candidate.fabric)),
    );
    if (!entry) return null;
    consumedOrigin.add(entry.index);
    return entry;
  };

  currentFabrics.forEach((fabric, index) => {
    if (takeUnconsumed(fabric.name, (origin) => sameGeometry(origin, fabric))) return;
    const movedFrom = takeUnconsumed(fabric.name);
    if (movedFrom) movedOrigin.add(movedFrom.index);
    changedCurrent.add(index);
  });

  return {
    originIndexes: [...movedOrigin].sort((left, right) => left - right),
    improvedIndexes: [...changedCurrent].sort((left, right) => left - right),
  };
}
