import { describe, expect, it } from 'vitest';
import type { SimulationDrawing } from '../types';
import {
  addSprayedAgents,
  createUniformPlacement,
  eraseAgents,
  isValidAgentPosition,
  parseHighlightedAgentId,
  sprayAttemptCount,
} from './placement';

function drawing(width = 10, height = 10): SimulationDrawing {
  return {
    layoutId: 1,
    title: '테스트 도면',
    width,
    height,
    outsideBoundary: [
      { x: 0, y: 0 },
      { x: width, y: 0 },
      { x: width, y: height },
      { x: 0, y: height },
    ],
    walls: [{ name: '벽', startX: 5, startY: 0, endX: 5, endY: 5 }],
    pillars: [],
    fabrics: [],
    layoutTexts: [],
    exits: [{ id: 1, name: '출구', startX: 7, startY: 10, endX: 8, endY: 10 }],
  };
}

describe('에이전트 배치', () => {
  it('최소 스프레이는 유효한 위치에 한 명만 배치한다', () => {
    expect(sprayAttemptCount(0.3)).toBe(1);
    expect(addSprayedAgents({ x: 2, y: 2 }, 0.3, drawing(), [], () => 0)).toEqual([{ x: 2, y: 2 }]);
  });

  it('벽과 겹치는 스프레이 후보는 버린다', () => {
    expect(addSprayedAgents({ x: 5, y: 2 }, 0.3, drawing(), [], () => 0)).toHaveLength(0);
    expect(isValidAgentPosition({ x: 0.3, y: 2 }, drawing())).toBe(false);
    expect(isValidAgentPosition({ x: 0.301, y: 2 }, drawing())).toBe(true);
    expect(isValidAgentPosition({ x: 4.7, y: 2 }, drawing())).toBe(false);
    expect(isValidAgentPosition({ x: 4.699, y: 2 }, drawing())).toBe(true);
  });

  it('균등 배치는 요청 인원만큼 새 좌표 집합을 만들고 수용량을 넘지 않는다', () => {
    const result = createUniformPlacement(20, drawing(), 17);
    expect(result.positions).toHaveLength(20);
    expect(result.capacity).toBeGreaterThanOrEqual(20);
    expect(new Set(result.positions.map(({ x, y }) => `${x}:${y}`)).size).toBe(20);
    expect(result.positions.every(({ x, y }) => x >= 0.301 && y >= 0.301)).toBe(true);

    const limited = createUniformPlacement(5000, drawing(1.2, 1.2), 17);
    expect(limited.positions).toHaveLength(limited.capacity);
    expect(limited.capacity).toBeLessThan(5000);
  });

  it('지우개 반지름 안의 에이전트만 제거한다', () => {
    expect(
      eraseAgents({ x: 2, y: 2 }, 0.8, [
        { x: 2, y: 2 },
        { x: 2.5, y: 2 },
        { x: 4, y: 4 },
      ]),
    ).toEqual([{ x: 4, y: 4 }]);
  });

  it('지우개 크기에 따라 제거 범위가 달라진다', () => {
    const agents = [
      { x: 2, y: 2 },
      { x: 3, y: 2 },
      { x: 4, y: 2 },
    ];

    expect(eraseAgents({ x: 2, y: 2 }, 0.5, agents)).toEqual([
      { x: 3, y: 2 },
      { x: 4, y: 2 },
    ]);
    expect(eraseAgents({ x: 2, y: 2 }, 1.5, agents)).toEqual([{ x: 4, y: 2 }]);
  });

  it('URL의 에이전트 번호가 실제 인원 범위 안에 있을 때만 강조한다', () => {
    expect(parseHighlightedAgentId('1', 3)).toBe(1);
    expect(parseHighlightedAgentId('3', 3)).toBe(3);
    expect(parseHighlightedAgentId('0', 3)).toBeNull();
    expect(parseHighlightedAgentId('4', 3)).toBeNull();
    expect(parseHighlightedAgentId('1.5', 3)).toBeNull();
    expect(parseHighlightedAgentId(null, 3)).toBeNull();
  });
});
