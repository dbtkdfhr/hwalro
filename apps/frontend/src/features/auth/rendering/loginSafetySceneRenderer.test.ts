import { describe, expect, it } from 'vitest';
import {
  calculateLoginSceneViewHeight,
  calculateLoginSceneTargetRotation,
  createRoute,
  INSIDE_WALLS,
  LOGIN_INSIDE_WALL_HEIGHT,
  LOGIN_OUTSIDE_WALL_HEIGHT,
  LOGIN_SCENE_ROUTE_POINTS,
  OUTSIDE_WALLS,
  type WallSegment,
} from './loginSafetySceneRenderer';

const AGENT_RADIUS = 0.14;
const WALL_HALF_DEPTH = 0.14;
const CLEARANCE = AGENT_RADIUS + WALL_HALF_DEPTH + 0.04;

function pointToSegmentDistance(x: number, z: number, wall: WallSegment): number {
  const deltaX = wall.endX - wall.startX;
  const deltaZ = wall.endZ - wall.startZ;
  const lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
  const projection = ((x - wall.startX) * deltaX + (z - wall.startZ) * deltaZ) / lengthSquared;
  const ratio = Math.max(0, Math.min(1, projection));
  const closestX = wall.startX + deltaX * ratio;
  const closestZ = wall.startZ + deltaZ * ratio;
  return Math.hypot(x - closestX, z - closestZ);
}

describe('login safety scene routes', () => {
  it('에이전트 이동 경로가 벽 두께와 에이전트 반지름만큼 벽에서 떨어져 있다', () => {
    const walls = [...OUTSIDE_WALLS, ...INSIDE_WALLS];
    let closest = {
      distance: Number.POSITIVE_INFINITY,
      routeIndex: -1,
      sampleIndex: -1,
      wallIndex: -1,
      x: 0,
      z: 0,
    };

    LOGIN_SCENE_ROUTE_POINTS.map(createRoute).forEach((route, routeIndex) => {
      for (let index = 0; index <= 1000; index += 1) {
        const point = route.getPointAt(index / 1000);
        walls.forEach((wall, wallIndex) => {
          const distance = pointToSegmentDistance(point.x, point.z, wall);
          if (distance < closest.distance) {
            closest = {
              distance,
              routeIndex,
              sampleIndex: index,
              wallIndex,
              x: point.x,
              z: point.z,
            };
          }
        });
      }
    });

    expect(closest.distance, JSON.stringify(closest)).toBeGreaterThanOrEqual(CLEARANCE);
  });
});

describe('login safety scene camera framing', () => {
  it('넓은 화면일수록 도면 위아래 안전 여백을 더 확보한다', () => {
    const standardViewHeight = calculateLoginSceneViewHeight(4 / 3);
    const projectorViewHeight = calculateLoginSceneViewHeight(16 / 9);
    const wideViewHeight = calculateLoginSceneViewHeight(21 / 9);

    expect(standardViewHeight).toBe(14.2);
    expect(projectorViewHeight).toBeGreaterThan(standardViewHeight);
    expect(wideViewHeight).toBeGreaterThan(projectorViewHeight);
    expect(wideViewHeight).toBeLessThanOrEqual(15.2);
  });

  it('비상구보다 낮은 벽 높이로 내부 동선을 드러낸다', () => {
    expect(LOGIN_OUTSIDE_WALL_HEIGHT).toBe(1.82);
    expect(LOGIN_INSIDE_WALL_HEIGHT).toBe(1.48);
    expect(INSIDE_WALLS.every((wall) => wall.height === LOGIN_INSIDE_WALL_HEIGHT)).toBe(true);
  });

  it('커서 위치에 따라 기본 각도보다 넓은 범위로 회전한다', () => {
    expect(calculateLoginSceneTargetRotation(-0.5)).toBeCloseTo(-0.14);
    expect(calculateLoginSceneTargetRotation(0)).toBeCloseTo(-0.05);
    expect(calculateLoginSceneTargetRotation(0.5)).toBeCloseTo(0.04);
  });
});
