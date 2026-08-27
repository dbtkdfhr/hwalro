import { describe, expect, it } from 'vitest';
import { HAZARD_GRADIENT_FRAGMENT_SHADER, HAZARD_GRADIENT_STOPS } from './hazardPresentation';

describe('hazardPresentation', () => {
  it('2D 위험 구역과 동일한 빨간색 방사형 그라데이션 단계를 제공한다', () => {
    expect(HAZARD_GRADIENT_STOPS.map(({ offset, css }) => [offset, css])).toEqual([
      [0, 'rgba(177, 32, 32, 0.58)'],
      [0.5, 'rgba(225, 75, 75, 0.28)'],
      [1, 'rgba(239, 119, 119, 0.08)'],
    ]);
  });

  it('3D 셰이더가 중심부터 외곽까지 두 구간으로 색상과 투명도를 보간한다', () => {
    expect(HAZARD_GRADIENT_FRAGMENT_SHADER).toContain('distance(vHazardUv, vec2(0.5))');
    expect(HAZARD_GRADIENT_FRAGMENT_SHADER).toContain('mix(innerColor, middleColor');
    expect(HAZARD_GRADIENT_FRAGMENT_SHADER).toContain('mix(middleColor, outerColor');
  });
});
