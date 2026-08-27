import { describe, expect, it } from 'vitest';
import { getExitPresentation } from './exitPresentation';

describe('getExitPresentation', () => {
  it('활성 비상구는 밝은 라임색으로 표시한다', () => {
    expect(getExitPresentation(true).color).toBe(0xabcf30);
  });

  it('비활성 비상구는 에메랄드색으로 표시한다', () => {
    expect(getExitPresentation(false).color).toBe(0x0f766e);
  });
});
