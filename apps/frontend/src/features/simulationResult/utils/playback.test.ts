import { describe, expect, it } from 'vitest';
import { isAgentPositionActive } from './playback';

describe('simulation playback positions', () => {
  it('에이전트 수가 아니라 ID별 좌표로 활성 상태를 판별한다', () => {
    const positions = new Float32Array([-10_000, -10_000, 12, 7, -10_000, -10_000]);

    expect(isAgentPositionActive(positions, 0)).toBe(false);
    expect(isAgentPositionActive(positions, 2)).toBe(true);
    expect(isAgentPositionActive(positions, 4)).toBe(false);
  });
});
