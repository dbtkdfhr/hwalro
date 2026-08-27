import { describe, expect, it } from 'vitest';
import { createEmptyDocument, nextOutsideWallName } from './document';
import { fromSerialized } from './serialization';

describe('외곽벽 이름', () => {
  it('새 외곽벽에 올바른 이름을 부여한다', () => {
    expect(nextOutsideWallName(createEmptyDocument())).toBe('외곽벽 1');
  });

  it('기존 외각벽 이름을 불러올 때 외곽벽으로 바로잡는다', () => {
    const document = fromSerialized({
      name: '테스트 도면',
      width: 10,
      height: 10,
      outsideWalls: [{ name: '외각벽 1', startX: 0, startY: 0, endX: 10, endY: 0 }],
    });

    expect(document.outsideWalls[0]?.name).toBe('외곽벽 1');
    expect(nextOutsideWallName(document)).toBe('외곽벽 2');
  });
});
