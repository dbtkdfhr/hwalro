import { describe, expect, it } from 'vitest';
import { worldToScene } from './floorPlanGeometry';
import type { SimulationDrawing } from '../../features/simulationResult/types';
import { FLOOR_PLAN_DIMENSIONS, floorPlanSceneInputOf, sceneCoordinate } from './floorPlanScene';

describe('floorPlanScene 계약', () => {
  it('좌표 변환이 기존 3D 장면과 같은 결과를 준다', () => {
    // 공용 계약이 기존 렌더러와 다른 좌표를 주면 12~14번에서 구현을 옮길 때 건물이 통째로 밀린다.
    for (const [x, y] of [
      [0, 0],
      [170, 100],
      [42.5, 17.25],
    ]) {
      expect(sceneCoordinate(x, y, 170, 100)).toEqual(worldToScene(x, y, 170, 100));
    }
  });

  it('높이와 두께가 기존 장면의 값과 일치한다', () => {
    // 이 값들이 조용히 바뀌면 시뮬레이션 3D의 벽 높이가 달라진다. 옮기는 동안의 기준선이다.
    expect(FLOOR_PLAN_DIMENSIONS.outsideWallHeight).toBe(3.2);
    expect(FLOOR_PLAN_DIMENSIONS.outsideWallThickness).toBe(0.34);
    expect(FLOOR_PLAN_DIMENSIONS.innerWallHeight).toBe(2.8);
    expect(FLOOR_PLAN_DIMENSIONS.innerWallThickness).toBe(0.24);
    expect(FLOOR_PLAN_DIMENSIONS.pillarHeight).toBe(2.5);
    expect(FLOOR_PLAN_DIMENSIONS.fabricHeight).toBe(1.55);
  });

  it('시뮬레이션 도면을 그대로 받아 정적 입력으로 옮긴다', () => {
    // 시뮬레이션 타입이 계약에 맞지 않으면 여기서 컴파일이 깨진다. 계약이 현실과 어긋나는 것을 막는 장치다.
    const drawing: SimulationDrawing = {
      name: '더현대 지하 2층',
      width: 170,
      height: 100,
      outsideBoundary: [
        { x: 0, y: 0 },
        { x: 170, y: 0 },
        { x: 170, y: 100 },
        { x: 0, y: 100 },
      ],
      walls: [{ name: '벽 1', startX: 0, startY: 0, endX: 10, endY: 0 }],
      exits: [
        { id: 9, name: '비상구 1', startX: 169, startY: 40, endX: 169, endY: 42, active: true },
      ],
      pillars: [{ name: '기둥 1', startX: 5, startY: 5, endX: 6, endY: 6, rotation: 0 }],
      fabrics: [{ name: '구조물 1', startX: 20, startY: 20, endX: 24, endY: 26, rotation: 15 }],
      layoutTexts: [{ text: 'MLB', x: 44, y: 9 }],
      zones: [],
    };

    const input = floorPlanSceneInputOf(drawing);

    expect(input.width).toBe(170);
    expect(input.walls).toHaveLength(1);
    expect(input.exits[0].active).toBe(true);
    // 도면 텍스트는 장면에서 라벨로 쓰인다. 이름이 바뀌는 지점이라 명시적으로 확인한다.
    expect(input.labels[0].text).toBe('MLB');
  });
});
