import { describe, expect, it } from 'vitest';
import { EXIT_COLOR_COUNT, exitColorOf } from './exitColors';

describe('exitColorOf', () => {
  const exits = [11, 22, 33, 44];

  it('비상구마다 다른 색을 준다', () => {
    const colors = exits.map((id) => exitColorOf(id, exits));
    expect(new Set(colors).size).toBe(exits.length);
  });

  it('같은 비상구는 항상 같은 색이다', () => {
    expect(exitColorOf(22, exits)).toBe(exitColorOf(22, exits));
  });

  it('ID가 아니라 도면 안의 순서로 색을 정한다', () => {
    // 저장할 때마다 요소 ID가 새로 매겨지므로, ID를 직접 쓰면 색이 통째로 바뀐다.
    const renumbered = [101, 102, 103, 104];
    expect(exitColorOf(102, renumbered)).toBe(exitColorOf(22, exits));
  });

  it('비상구가 색 수보다 많아도 색을 돌려준다', () => {
    const many = Array.from({ length: EXIT_COLOR_COUNT + 5 }, (_, index) => index + 1);
    expect(exitColorOf(many[many.length - 1], many)).toMatch(/^#[0-9a-f]{6}$/i);
  });

  it('실제 도면 규모(비상구 16개)를 모두 다른 색으로 덮는다', () => {
    const sixteen = Array.from({ length: 16 }, (_, index) => index + 1);
    expect(new Set(sixteen.map((id) => exitColorOf(id, sixteen))).size).toBe(16);
  });
});
