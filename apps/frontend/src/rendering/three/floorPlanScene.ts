/**
 * 도면 한 층을 3D로 세우는 데 필요한 정적 입력 계약.
 *
 * <p>지금 3D 장면은 시뮬레이션 결과 화면 안에 있고 `SimulationResultViewModel`(에이전트 프레임·병목·재생 시각)에
 * 묶여 있다. 도면 화면에서도 같은 건물을 세우려면 "움직이지 않는 부분"만 따로 떼어 낼 수 있어야 한다.
 *
 * 이 파일은 그 경계를 타입으로만 정한다. 렌더러 구현은 아직 옮기지 않는다.
 */

/** 도면 좌표계의 점(m). 왼쪽 위가 원점이다. 장면 좌표(x, z)와 헷갈리지 않도록 이름을 구분한다. */
export interface PlanPoint {
  x: number;
  y: number;
}

/** 벽·비상구처럼 두 점으로 정의되는 요소. */
export interface SceneSegment {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

/** 기둥·구조물처럼 사각형으로 정의되는 요소. 회전은 도(degree)다. */
export interface SceneRect extends SceneSegment {
  rotation?: number;
}

export interface SceneExit extends SceneSegment {
  id: number;
  /** 이번 계산에서 실제로 쓰이는 비상구인지. 꺼진 비상구는 외벽을 뚫지 않는다. */
  active: boolean;
}

export interface SceneLabel {
  text: string;
  x: number;
  y: number;
}

/**
 * 정적 장면을 세우는 데 필요한 전부.
 *
 * <p>여기에 없는 것은 정적 장면의 입력이 아니다. 에이전트, 히트맵, 병목, 위험 구역, 재생 시각, 대피 경로는 모두 이 위에
 * 얹는 별도 레이어이며 이 타입에 넣지 않는다. 넣는 순간 도면 화면이 시뮬레이션 개념을 알아야 한다.
 */
export interface FloorPlanSceneInput {
  /** 도면 가로 길이(m). */
  width: number;
  /** 도면 세로 길이(m). */
  height: number;
  /**
   * 건물 외곽을 이루는 닫힌 폐곡선의 정점들.
   *
   * <p>바닥 슬래브의 모양과 외벽 위치가 모두 이 폴리곤에서 나온다. 시뮬레이션 쪽은 서버가 조립해 준 값을 그대로 쓰고,
   * 도면 쪽은 외곽벽 선분에서 폴리곤을 만들어 넣어야 한다.
   */
  outsideBoundary: PlanPoint[];
  /** 건물 안쪽 벽. */
  walls: SceneSegment[];
  pillars: SceneRect[];
  fabrics: SceneRect[];
  exits: SceneExit[];
  /** 도면 텍스트. 에스컬레이터처럼 이름으로 알아보는 구조물을 세우는 데 쓴다. */
  labels: SceneLabel[];
}

/**
 * 요소별 높이와 두께(m).
 *
 * <p>지금 시뮬레이션 렌더러가 쓰는 값 그대로다. 12~14번에서 구현을 옮길 때 이 값이 바뀌면 화면이 달라지므로, 옮기는
 * 동안에는 여기를 기준으로 삼는다.
 */
export const FLOOR_PLAN_DIMENSIONS = {
  outsideWallHeight: 3.2,
  outsideWallThickness: 0.34,
  innerWallHeight: 2.8,
  innerWallThickness: 0.24,
  pillarHeight: 2.5,
  fabricHeight: 1.55,
  /** 바닥 슬래브가 도면 바깥으로 더 나가는 여유(m). 배경판이 잘려 보이지 않게 한다. */
  backdropMargin: 8,
} as const;

/**
 * 정적 장면의 색.
 *
 * <p>3D는 조명과 재질이 함께 색을 만든다. 여기서는 재질의 기본색만 정하고 거칠기·금속성은 렌더러가 정한다 - 값 하나를
 * 두 곳에서 정의하면 둘이 갈라진다.
 */
export const FLOOR_PLAN_COLORS = {
  backdrop: 0xdfe6e3,
  floor: 0xfafcfb,
  outsideWall: 0x667d77,
  innerWall: 0xb7c6c2,
} as const;

/**
 * 도면 좌표를 장면 좌표로 옮긴다.
 *
 * <p>도면은 왼쪽 위가 원점인 2D 좌표계이고 장면은 가운데가 원점인 3D 좌표계다. y가 뒤집히는 이유가 여기 있다 - 도면의
 * 아래쪽이 장면에서는 앞쪽(+z)이다.
 */
export function sceneCoordinate(x: number, y: number, width: number, height: number) {
  return { x: x - width / 2, z: y - height / 2 };
}

/**
 * 시뮬레이션 결과의 도면을 정적 장면 입력으로 옮긴다.
 *
 * <p>필드 이름이 거의 같아 보이지만 이 함수가 있어야 하는 이유는 <b>방향</b>이다. 공용 장면이 시뮬레이션 타입을 알면
 * 도면 화면도 시뮬레이션을 알아야 한다. 변환을 여기 한 곳에 두어 의존이 한쪽으로만 흐르게 한다.
 */
export function floorPlanSceneInputOf(drawing: {
  width: number;
  height: number;
  outsideBoundary: PlanPoint[];
  walls: SceneSegment[];
  pillars: SceneRect[];
  fabrics: SceneRect[];
  exits: SceneExit[];
  layoutTexts: SceneLabel[];
}): FloorPlanSceneInput {
  return {
    width: drawing.width,
    height: drawing.height,
    outsideBoundary: drawing.outsideBoundary,
    walls: drawing.walls,
    pillars: drawing.pillars,
    fabrics: drawing.fabrics,
    exits: drawing.exits,
    labels: drawing.layoutTexts,
  };
}
