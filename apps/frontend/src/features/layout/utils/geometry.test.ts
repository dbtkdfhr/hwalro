import { describe, expect, it } from 'vitest';
import { centerCameraOnPoint, PX_PER_METER, worldToScreen } from './geometry';

describe('camera centering', () => {
  it('centers a world point without changing zoom', () => {
    const camera = centerCameraOnPoint(
      { zoom: 1.5, panX: 0, panY: 0 },
      { x: 70, y: 50 },
      200,
      150,
      700,
      420,
    );
    const screen = worldToScreen({ x: 70, y: 50 }, { left: 0, top: 0 }, camera);

    expect(camera.zoom).toBe(1.5);
    expect(screen.x).toBeCloseTo(350);
    expect(screen.y).toBeCloseTo(210);
    expect(PX_PER_METER).toBeGreaterThan(0);
  });

  it('clamps document-edge targets to the existing pan range', () => {
    const camera = centerCameraOnPoint(
      { zoom: 2, panX: 10, panY: 10 },
      { x: -1000, y: -1000 },
      200,
      150,
      700,
      420,
    );

    expect(Number.isFinite(camera.panX)).toBe(true);
    expect(Number.isFinite(camera.panY)).toBe(true);
    expect(camera.panX).toBeGreaterThanOrEqual(-25);
    expect(camera.panY).toBeGreaterThanOrEqual(-15);
  });
});
