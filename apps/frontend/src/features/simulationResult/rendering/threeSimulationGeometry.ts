import type { Bounds } from '../types';
import { worldToScene } from '../../../rendering/three/floorPlanGeometry';

/**
 * 시뮬레이션 결과 위에 얹는 사각 영역(병목, 위험 구역)의 3D 배치.
 *
 * <p>정적 건물이 아니라 시뮬레이션이 만들어 낸 결과를 표시하는 계산이라 공용 장면으로 옮기지 않는다.
 */
export function boundsTransform(bounds: Bounds, worldWidth: number, worldHeight: number) {
  const center = worldToScene(
    bounds.x + bounds.width / 2,
    bounds.y + bounds.height / 2,
    worldWidth,
    worldHeight,
  );
  return {
    ...center,
    width: Math.max(0.01, Math.abs(bounds.width)),
    depth: Math.max(0.01, Math.abs(bounds.height)),
  };
}
