import { describe, expect, it } from 'vitest';
import { AUTO_SCROLL_EDGE_PX, autoScrollStep } from './dragAutoScroll';

const rect = { top: 100, bottom: 400 };

describe('dragAutoScroll', () => {
  it('가운데에서는 스크롤하지 않는다', () => {
    expect(autoScrollStep(250, rect)).toBe(0);
  });

  it('위쪽 가장자리에서는 위로 굴린다', () => {
    expect(autoScrollStep(rect.top + 2, rect)).toBeLessThan(0);
  });

  it('아래쪽 가장자리에서는 아래로 굴린다', () => {
    expect(autoScrollStep(rect.bottom - 2, rect)).toBeGreaterThan(0);
  });

  it('가장자리에 가까울수록 빨라진다', () => {
    const near = Math.abs(autoScrollStep(rect.top + 1, rect));
    const far = Math.abs(autoScrollStep(rect.top + AUTO_SCROLL_EDGE_PX - 1, rect));
    expect(near).toBeGreaterThan(far);
  });
});
