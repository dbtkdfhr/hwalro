import { describe, expect, it } from 'vitest';
import type { Bounds, DrawingText } from '../types';
import { generateRiskZoneName } from './riskZoneName';

const DRAWING = { width: 100, height: 80, layoutTexts: [] as DrawingText[] };

function boundsAt(centerX: number, centerY: number): Bounds {
  return { x: centerX - 1, y: centerY - 1, width: 2, height: 2 };
}

describe('generateRiskZoneName', () => {
  it.each([
    [56, 40, '동쪽'],
    [44, 40, '서쪽'],
    [50, 34, '북쪽'],
    [50, 46, '남쪽'],
  ])('uses a nearby store and the %s/%s cardinal direction', (centerX, centerY, direction) => {
    expect(
      generateRiskZoneName(boundsAt(centerX as number, centerY as number), {
        ...DRAWING,
        layoutTexts: [{ text: 'MLB', x: 50, y: 40 }],
      }),
    ).toBe(`MLB 매장 ${direction} 통로`);
  });

  it('prefers the horizontal direction for an exact diagonal tie', () => {
    expect(
      generateRiskZoneName(boundsAt(56, 46), {
        ...DRAWING,
        layoutTexts: [{ text: 'MLB', x: 50, y: 40 }],
      }),
    ).toBe('MLB 매장 동쪽 통로');
  });

  it('normalizes multiline store names and does not duplicate the store suffix', () => {
    expect(
      generateRiskZoneName(boundsAt(50, 46), {
        ...DRAWING,
        layoutTexts: [{ text: '노메뉴얼\n코이세이오', x: 50, y: 40 }],
      }),
    ).toBe('노메뉴얼·코이세이오 매장 남쪽 통로');
    expect(
      generateRiskZoneName(boundsAt(56, 40), {
        ...DRAWING,
        layoutTexts: [{ text: '현대 매장', x: 50, y: 40 }],
      }),
    ).toBe('현대 매장 동쪽 통로');
  });

  it.each([
    [90, 40, '동측 통로'],
    [10, 40, '서측 통로'],
    [50, 5, '북측 통로'],
    [50, 75, '남측 통로'],
    [50, 40, '중앙 통로'],
  ])('uses a simple drawing-relative fallback at %s/%s', (centerX, centerY, expected) => {
    expect(generateRiskZoneName(boundsAt(centerX as number, centerY as number), DRAWING)).toBe(
      expected,
    );
  });

  it('ignores blank text and falls back when the nearest store is beyond the distance limit', () => {
    expect(
      generateRiskZoneName(boundsAt(50, 40), {
        ...DRAWING,
        layoutTexts: [
          { text: '   ', x: 50, y: 40 },
          { text: '멀리 있는 매장', x: 0, y: 0 },
        ],
      }),
    ).toBe('중앙 통로');
  });
});
