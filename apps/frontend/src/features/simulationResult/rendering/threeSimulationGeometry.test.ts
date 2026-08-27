import { describe, expect, it } from 'vitest';
import { boundsTransform } from './threeSimulationGeometry';

describe('threeSimulationGeometry', () => {
  it('영역의 크기와 중심을 보존한다', () => {
    expect(boundsTransform({ x: 10, y: 5, width: 20, height: 10 }, 40, 20)).toEqual({
      x: 0,
      z: 0,
      width: 20,
      depth: 10,
    });
  });
});
