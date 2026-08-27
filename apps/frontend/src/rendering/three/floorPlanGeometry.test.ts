import { describe, expect, it } from 'vitest';
import {
  createBoundarySegments,
  drawingRectContainsPoint,
  escalatorPlacementFromRect,
  escalatorRunElevation,
  exitPortalPlacement,
  isEscalatorLabel,
  projectExitsToBoundarySegments,
  segmentTransform,
  splitBoundarySegmentAtExits,
  splitBoundarySegmentsAtActiveExits,
  splitBoundarySegmentsAtExits,
  worldToScene,
} from './floorPlanGeometry';

describe('threeSimulationGeometry', () => {
  it('도면 좌표의 중심을 Three.js 원점으로 옮긴다', () => {
    expect(worldToScene(50, 25, 100, 50)).toEqual({ x: 0, z: 0 });
  });

  it('벽 선분을 길이와 중심, 회전값으로 변환한다', () => {
    const transform = segmentTransform(
      { name: 'wall', startX: 10, startY: 10, endX: 10, endY: 30 },
      40,
      40,
    );
    expect(transform).toMatchObject({ x: -10, z: 0, length: 20 });
    expect(transform.rotationY).toBeCloseTo(-Math.PI / 2);
  });

  it('비상구 선분 안에 실제 개구부가 남는 문틀 치수를 계산한다', () => {
    const placement = exitPortalPlacement(
      { name: '비상구', startX: 4, startY: 0, endX: 6, endY: 0 },
      10,
      10,
    );

    expect(placement).toMatchObject({
      x: 0,
      z: -5,
      width: 2,
      postWidth: 0.24,
      postOffset: 0.88,
      openingWidth: 1.52,
    });
    expect(placement.rotationY).toBeCloseTo(0);
  });

  it.each(['E/S', 'ES', ' e / s ', 'e/s'])('%s 표기를 에스컬레이터로 인식한다', (text) => {
    expect(isEscalatorLabel(text)).toBe(true);
  });

  it('비슷한 매장명은 에스컬레이터로 오인하지 않는다', () => {
    expect(isEscalatorLabel('EQL')).toBe(false);
    expect(isEscalatorLabel('ESSENTIAL')).toBe(false);
  });

  it('에스컬레이터 좌표를 포함하는 구조물을 판별한다', () => {
    const structure = {
      name: '기둥',
      startX: 10,
      startY: 20,
      endX: 16,
      endY: 30,
      rotation: 0,
    };
    expect(drawingRectContainsPoint(structure, { x: 12, y: 24 })).toBe(true);
    expect(drawingRectContainsPoint(structure, { x: 8, y: 24 })).toBe(false);
  });

  it('회전된 구조물의 실제 영역을 기준으로 판별한다', () => {
    const structure = {
      name: '회전 구조물',
      startX: -4,
      startY: -1,
      endX: 4,
      endY: 1,
      rotation: 45,
    };
    expect(drawingRectContainsPoint(structure, { x: 2, y: 2 })).toBe(true);
    expect(drawingRectContainsPoint(structure, { x: 3, y: -3 })).toBe(false);
  });

  it('세로로 긴 기둥의 중심과 크기를 에스컬레이터 배치로 변환한다', () => {
    const placement = escalatorPlacementFromRect(
      {
        name: '기둥',
        startX: 20,
        startY: 10,
        endX: 24,
        endY: 26,
        rotation: 0,
      },
      40,
      40,
    );
    expect(placement).toMatchObject({ x: 2, z: -2, length: 16, width: 4 });
    expect(placement.rotationY).toBeCloseTo(-Math.PI / 2);
  });

  it('기둥의 회전값을 에스컬레이터 방향에 합성한다', () => {
    const placement = escalatorPlacementFromRect(
      {
        name: '회전 기둥',
        startX: 0,
        startY: 0,
        endX: 12,
        endY: 4,
        rotation: 30,
      },
      20,
      20,
    );
    expect(placement).toMatchObject({ length: 12, width: 4 });
    expect(placement.rotationY).toBeCloseTo(-Math.PI / 6);
  });

  it('두 에스컬레이터 운행부의 높이를 반대 방향으로 계산한다', () => {
    expect(escalatorRunElevation(0.25, 1, 2)).toBe(0.5);
    expect(escalatorRunElevation(0.25, -1, 2)).toBe(1.5);
    expect(escalatorRunElevation(0, 1, 2)).toBe(0);
    expect(escalatorRunElevation(0, -1, 2)).toBe(2);
  });

  it('외곽 경계의 마지막 점을 첫 점과 연결한다', () => {
    const segments = createBoundarySegments([
      { x: 0, y: 0 },
      { x: 10, y: 0 },
      { x: 10, y: 10 },
    ]);
    expect(segments).toHaveLength(3);
    expect(segments[2]).toMatchObject({ startX: 10, startY: 10, endX: 0, endY: 0 });
  });

  it('외곽벽에서 출구와 겹치는 구간을 비운다', () => {
    const walls = splitBoundarySegmentAtExits(
      { name: 'outside', startX: 0, startY: 0, endX: 10, endY: 0 },
      [{ name: 'exit', startX: 4, startY: 0, endX: 6, endY: 0 }],
    );
    expect(walls).toEqual([
      { name: 'outside-part-0', startX: 0, startY: 0, endX: 4, endY: 0 },
      { name: 'outside-part-1', startX: 6, startY: 0, endX: 10, endY: 0 },
    ]);
  });

  it('활성 비상구만 외곽벽을 뚫고 비활성 비상구 뒤는 막아 둔다', () => {
    const walls = splitBoundarySegmentsAtActiveExits(
      [{ name: 'outside', startX: 0, startY: 0, endX: 10, endY: 0 }],
      [
        {
          id: 1,
          name: 'active-exit',
          startX: 2,
          startY: 0,
          endX: 4,
          endY: 0,
          active: true,
        },
        {
          id: 2,
          name: 'inactive-exit',
          startX: 6,
          startY: 0,
          endX: 8,
          endY: 0,
          active: false,
        },
      ],
    );

    expect(walls).toEqual([
      { name: 'outside-part-0', startX: 0, startY: 0, endX: 2, endY: 0 },
      { name: 'outside-part-1', startX: 4, startY: 0, endX: 10, endY: 0 },
    ]);
  });

  it('외곽벽에서 떨어진 출구를 가장 가까운 평행 벽에 투영해 개구부를 만든다', () => {
    const walls = splitBoundarySegmentsAtExits(
      [
        { name: 'top', startX: 0, startY: 0, endX: 10, endY: 0 },
        { name: 'bottom', startX: 10, startY: 10, endX: 0, endY: 10 },
      ],
      [{ name: 'exit', startX: 4, startY: 1.36, endX: 6, endY: 1.36 }],
    );

    expect(walls).toEqual([
      { name: 'top-part-0', startX: 0, startY: 0, endX: 4, endY: 0 },
      { name: 'top-part-1', startX: 6, startY: 0, endX: 10, endY: 0 },
      { name: 'bottom-part-0', startX: 10, startY: 10, endX: 0, endY: 10 },
    ]);
  });

  it('외곽벽에서 떨어진 우측 비상구 문틀도 개구부와 같은 벽 평면으로 옮긴다', () => {
    const [projected] = projectExitsToBoundarySegments(
      [{ name: 'right-wall', startX: 146.183, startY: 43.2, endX: 146.183, endY: 46.18 }],
      [
        {
          name: '비상구 2',
          startX: 145.2819,
          startY: 43.2,
          endX: 145.2819,
          endY: 46.2,
        },
      ],
    );

    expect(projected.startX).toBeCloseTo(146.183);
    expect(projected.endX).toBeCloseTo(146.183);
    expect(projected.startY).toBeCloseTo(43.2);
    expect(projected.endY).toBeCloseTo(46.2);
  });

  it('연속된 동일 선상의 외곽벽 중 비상구와 가장 많이 겹치는 벽을 뚫는다', () => {
    const walls = splitBoundarySegmentsAtExits(
      [
        {
          name: 'right-upper-wall',
          startX: 146.183,
          startY: 64.04,
          endX: 146.183,
          endY: 46.18,
        },
        {
          name: 'right-exit-wall',
          startX: 146.183,
          startY: 46.18,
          endX: 146.183,
          endY: 43.2,
        },
      ],
      [
        {
          name: '비상구 2',
          startX: 145.2819,
          startY: 43.2,
          endX: 145.2819,
          endY: 46.2,
        },
      ],
    );

    expect(walls).toEqual([
      {
        name: 'right-upper-wall-part-0',
        startX: 146.183,
        startY: 64.04,
        endX: 146.183,
        endY: 46.18,
      },
    ]);
  });

  it('높이가 다른 외곽벽 모서리에서도 좌상단 비상구와 가장 많이 겹치는 벽을 뚫는다', () => {
    const walls = splitBoundarySegmentsAtActiveExits(
      [
        {
          name: 'upper-long-wall',
          startX: 42.177,
          startY: 5.87,
          endX: 70.924,
          endY: 5.87,
        },
        {
          name: 'upper-exit-wall',
          startX: 42.177,
          startY: 5.44,
          endX: 34.901,
          endY: 5.44,
        },
      ],
      [
        {
          id: 3,
          name: '비상구 3',
          startX: 40,
          startY: 6.358,
          endX: 42.2,
          endY: 6.358,
          active: true,
        },
      ],
    );

    expect(walls).toEqual([
      {
        name: 'upper-long-wall-part-0',
        startX: 42.177,
        startY: 5.87,
        endX: 70.924,
        endY: 5.87,
      },
      {
        name: 'upper-exit-wall-part-0',
        startX: 40,
        startY: 5.44,
        endX: 34.901,
        endY: 5.44,
      },
    ]);
  });

  it('외곽벽에서 충분히 멀리 떨어진 내부 출구는 개구부로 만들지 않는다', () => {
    const walls = splitBoundarySegmentsAtExits(
      [{ name: 'top', startX: 0, startY: 0, endX: 10, endY: 0 }],
      [{ name: 'internal-exit', startX: 4, startY: 5, endX: 6, endY: 5 }],
    );

    expect(walls).toEqual([{ name: 'top-part-0', startX: 0, startY: 0, endX: 10, endY: 0 }]);
  });
});
