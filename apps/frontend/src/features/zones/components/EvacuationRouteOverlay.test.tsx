// @vitest-environment happy-dom

import { act, forwardRef, useImperativeHandle, type PropsWithChildren } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { EvacuationRoute } from '../api/zoneApi';
import { EvacuationRouteOverlay } from './EvacuationRouteOverlay';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const batchDraw = vi.fn();

vi.mock('react-konva', () => ({
  Group: forwardRef(function MockGroup({ children }: PropsWithChildren, ref) {
    useImperativeHandle(ref, () => ({
      getLayer: () => ({ batchDraw }),
    }));
    return children ?? null;
  }),
  Line: forwardRef(function MockLine(_props, ref) {
    useImperativeHandle(ref, () => ({
      position: vi.fn(),
      rotation: vi.fn(),
      visible: vi.fn(),
      getLayer: () => ({ batchDraw }),
    }));
    return null;
  }),
  Circle: () => null,
}));

const route: EvacuationRoute = {
  zoneId: 7,
  zoneName: '전시장 A',
  origin: { x: 0, y: 0 },
  routeOrigin: { x: 0, y: 0 },
  originAdjusted: false,
  status: 'AVAILABLE',
  unavailableReason: null,
  defaultExit: null,
  recommendedExitId: 11,
  recommendedExitName: '비상구 A',
  exitChoice: 'NEAREST',
  distanceMeters: 10,
  narrowestMeters: null,
  waypoints: [],
  partitions: [
    {
      exitId: 11,
      exitName: '비상구 A',
      entryPoint: { x: 1, y: 1 },
      waypoints: [
        { x: 1, y: 1 },
        { x: 2, y: 1 },
      ],
      distanceMeters: 5,
      narrowestMeters: null,
    },
    {
      exitId: 12,
      exitName: '비상구 B',
      entryPoint: { x: 3, y: 3 },
      waypoints: [
        { x: 3, y: 3 },
        { x: 4, y: 3 },
      ],
      distanceMeters: 6,
      narrowestMeters: null,
    },
  ],
};

let container: HTMLDivElement;
let root: Root;
let animationFrame: FrameRequestCallback | null;

beforeEach(() => {
  batchDraw.mockClear();
  animationFrame = null;
  vi.spyOn(window, 'matchMedia').mockReturnValue({ matches: false } as MediaQueryList);
  vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
    animationFrame = callback;
    return 1;
  });
  vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => undefined);
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.restoreAllMocks();
});

describe('EvacuationRouteOverlay', () => {
  it('draws the route layer once per frame even when a zone has multiple branches', () => {
    act(() => {
      root.render(<EvacuationRouteOverlay routes={[route]} exitIds={[11, 12]} scale={(x) => x} />);
    });

    const callback = animationFrame;
    expect(callback).not.toBeNull();
    act(() => callback?.(performance.now() + 16));

    expect(batchDraw).toHaveBeenCalledOnce();
  });
});
