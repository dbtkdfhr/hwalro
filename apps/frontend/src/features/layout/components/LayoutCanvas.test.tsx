// @vitest-environment happy-dom

import { act, type PropsWithChildren } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createInitialState } from '../state/editorReducer';
import { PX_PER_METER } from '../utils/geometry';
import { LayoutCanvas } from './LayoutCanvas';

vi.mock('react-konva', () => {
  const Shape = ({ children }: PropsWithChildren) => children ?? null;
  const Text = ({ text }: { text?: string }) => <span data-konva-text>{text}</span>;
  return {
    Circle: Shape,
    Group: Shape,
    Layer: Shape,
    Line: Shape,
    Rect: Shape,
    Stage: Shape,
    Text,
  };
});

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

class TestResizeObserver {
  observe() {}
  disconnect() {}
}

globalThis.ResizeObserver = TestResizeObserver as unknown as typeof ResizeObserver;

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe('LayoutCanvas employee selection mode', () => {
  it('selects a structure on a locked drawing without starting any geometry edit', () => {
    const initial = createInitialState();
    const state = {
      ...initial,
      doc: {
        ...initial.doc,
        fabrics: [
          {
            id: 'f1',
            backendId: 20,
            name: '진열대',
            startX: 1,
            startY: 1,
            endX: 3,
            endY: 2,
            rotation: 0,
          },
        ],
      },
    };
    const dispatch = vi.fn();
    act(() => {
      root.render(
        <LayoutCanvas
          state={state}
          dispatch={dispatch}
          size={{ w: 0, h: 0 }}
          onSizeChange={vi.fn()}
          readOnly
          geometryEditable={false}
        />,
      );
    });
    const canvas = container.querySelector<HTMLDivElement>('[aria-label="도면 캔버스"]');
    expect(canvas).not.toBeNull();
    Object.defineProperties(canvas, {
      getBoundingClientRect: {
        value: () => ({ top: 0, left: 0, right: 500, bottom: 500, width: 500, height: 500 }),
      },
      setPointerCapture: { value: vi.fn() },
    });

    act(() => {
      canvas?.dispatchEvent(
        new PointerEvent('pointerdown', {
          bubbles: true,
          button: 0,
          pointerId: 1,
          clientX: 2 * PX_PER_METER,
          clientY: 1.5 * PX_PER_METER,
        }),
      );
    });

    expect(dispatch).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'selectAt', fabricId: 'f1' }),
    );
    const actionTypes = dispatch.mock.calls.map(([action]) => action.type);
    expect(actionTypes).not.toContain('dragStartMove');
    expect(actionTypes).not.toContain('resizeStart');
    expect(actionTypes).not.toContain('rotateStart');
  });
});

describe('LayoutCanvas risk mode', () => {
  it('allows drawing a risk zone on a locked layout', () => {
    const onRiskZoneDrawn = vi.fn();
    act(() => {
      root.render(
        <LayoutCanvas
          state={createInitialState()}
          dispatch={vi.fn()}
          size={{ w: 800, h: 600 }}
          onSizeChange={vi.fn()}
          readOnly
          riskMode
          onRiskZoneDrawn={onRiskZoneDrawn}
        />,
      );
    });

    const canvas = container.querySelector<HTMLElement>('[aria-label="도면 캔버스"]');
    expect(canvas).not.toBeNull();
    if (!canvas) return;
    canvas.setPointerCapture = vi.fn();
    canvas.releasePointerCapture = vi.fn();
    canvas.hasPointerCapture = vi.fn(() => true);

    act(() => {
      canvas.dispatchEvent(
        new PointerEvent('pointerdown', {
          bubbles: true,
          button: 0,
          clientX: 40,
          clientY: 40,
          pointerId: 1,
        }),
      );
    });
    act(() => {
      canvas.dispatchEvent(
        new PointerEvent('pointermove', {
          bubbles: true,
          clientX: 43,
          clientY: 43,
          pointerId: 1,
        }),
      );
    });
    act(() => {
      canvas.dispatchEvent(
        new PointerEvent('pointerup', { bubbles: true, clientX: 43, clientY: 43, pointerId: 1 }),
      );
    });

    expect(onRiskZoneDrawn).toHaveBeenCalledOnce();
    expect(onRiskZoneDrawn.mock.calls[0][0].width).toBeLessThan(0.5);
  });
});

describe('LayoutCanvas zone rendering', () => {
  it('does not render the zone name on the canvas', () => {
    act(() => {
      root.render(
        <LayoutCanvas
          state={createInitialState()}
          dispatch={vi.fn()}
          size={{ w: 800, h: 600 }}
          onSizeChange={vi.fn()}
          zones={[
            {
              zoneId: 1,
              name: '좌상단 구역 이름',
              zoneType: 'WORK',
              rect: { x: 10, y: 10, width: 100, height: 80 },
              assignedUserId: null,
              defaultExitId: null,
              displayOrder: 0,
              members: [],
            },
          ]}
        />,
      );
    });

    expect(container.textContent).not.toContain('좌상단 구역 이름');
  });
});
