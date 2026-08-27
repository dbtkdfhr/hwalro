import { describe, expect, it } from 'vitest';
import { findImprovedFabricDiff } from './improvedFabrics';
import type { DrawingRect } from '../types';

function fabric(name: string, startX: number, startY: number, rotation = 0): DrawingRect {
  return {
    name,
    startX,
    startY,
    endX: startX + 4,
    endY: startY + 2,
    rotation,
  };
}

describe('findImprovedFabricDiff', () => {
  it('marks moved and added fabrics on the improved side', () => {
    const origin = [
      fabric('무대', 0, 0),
      fabric('테이블', 10, 10, 90),
      fabric('출구 안내판', 20, 0),
    ];
    const current = [
      fabric('무대', 1, 1),
      fabric('테이블', 10, 10, 90),
      fabric('출구 안내판', 22, 2, 45),
      fabric('신규 바리케이드', 30, 30),
    ];

    expect(findImprovedFabricDiff(origin, current)).toEqual({
      originIndexes: [0, 2],
      improvedIndexes: [0, 2, 3],
    });
  });

  it('returns nothing when layouts are identical or origin is unknown', () => {
    const fabrics = [fabric('무대', 0, 0), fabric('테이블', 10, 10)];

    expect(findImprovedFabricDiff(fabrics, [...fabrics])).toEqual({
      originIndexes: [],
      improvedIndexes: [],
    });
    expect(findImprovedFabricDiff([], fabrics)).toEqual({
      originIndexes: [],
      improvedIndexes: [0, 1],
    });
  });

  it('pairs duplicated names by geometry before pairing leftovers as moves', () => {
    const origin = [fabric('의자', 0, 0), fabric('의자', 10, 10)];
    const current = [fabric('의자', 10, 10), fabric('의자', 5, 5), fabric('의자', 0, 0)];

    expect(findImprovedFabricDiff(origin, current)).toEqual({
      originIndexes: [0],
      improvedIndexes: [1, 2],
    });
  });
});
