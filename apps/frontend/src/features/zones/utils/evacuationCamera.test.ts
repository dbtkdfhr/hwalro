import { describe, expect, it } from 'vitest';
import { PX_PER_METER } from '../../layout/utils/geometry';
import { fitEvacuationCamera } from './evacuationCamera';

describe('fitEvacuationCamera', () => {
  it('keeps the route origin and exit visible in the initial viewport', () => {
    const camera = fitEvacuationCamera(
      [
        { x: 10, y: 20 },
        { x: 90, y: 70 },
      ],
      900,
      460,
    );
    const visibleWidth = 900 / (camera.zoom * PX_PER_METER);
    const visibleHeight = 460 / (camera.zoom * PX_PER_METER);

    expect(camera.panX).toBeLessThan(10);
    expect(camera.panY).toBeLessThan(20);
    expect(camera.panX + visibleWidth).toBeGreaterThan(90);
    expect(camera.panY + visibleHeight).toBeGreaterThan(70);
  });

  it('fills the viewport for a short evacuation route', () => {
    const camera = fitEvacuationCamera(
      [
        { x: 10, y: 10 },
        { x: 20, y: 20 },
      ],
      1200,
      700,
    );

    expect(camera.zoom).toBe(4);
  });
});
