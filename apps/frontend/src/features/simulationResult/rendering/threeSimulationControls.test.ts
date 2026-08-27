import { describe, expect, it } from 'vitest';
import { MOUSE } from 'three';
import type { OrbitControls } from 'three/addons/controls/OrbitControls.js';

import { configureThreeSimulationControls } from './threeSimulationControls';

describe('configureThreeSimulationControls', () => {
  it('도면 탐색에 맞게 우클릭 이동을 바닥면에 고정하고 커서 중심 줌을 사용한다', () => {
    const controls = {
      enableDamping: true,
      screenSpacePanning: true,
      panSpeed: 1,
      zoomToCursor: false,
      minDistance: 0,
      maxDistance: 0,
      maxPolarAngle: Math.PI,
      mouseButtons: {
        LEFT: MOUSE.PAN,
        MIDDLE: MOUSE.ROTATE,
        RIGHT: MOUSE.DOLLY,
      },
    } as unknown as OrbitControls;

    configureThreeSimulationControls(controls, 100);

    expect(controls.enableDamping).toBe(false);
    expect(controls.screenSpacePanning).toBe(false);
    expect(controls.panSpeed).toBe(0.75);
    expect(controls.zoomToCursor).toBe(true);
    expect(controls.mouseButtons).toEqual({
      LEFT: MOUSE.ROTATE,
      MIDDLE: MOUSE.DOLLY,
      RIGHT: MOUSE.PAN,
    });
    expect(controls.minDistance).toBe(22);
    expect(controls.maxDistance).toBe(280);
    expect(controls.maxPolarAngle).toBeCloseTo(Math.PI * 0.47);
  });
});
