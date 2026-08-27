// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LayoutZone } from '../../layout/api/layoutMetadataApi';
import { EvacuationRoutePanel } from './EvacuationRoutePanel';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const selectedZone: LayoutZone = {
  zoneId: 7,
  name: '전시장 A',
  zoneType: 'WORK',
  assignedUserId: null,
  defaultExitId: null,
  displayOrder: 0,
  rect: { x: 1, y: 2, width: 3, height: 4 },
  members: [],
};

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

describe('EvacuationRoutePanel', () => {
  it('shows one switch for the selected zone', () => {
    const onToggle = vi.fn();
    act(() => {
      root.render(
        <EvacuationRoutePanel
          zone={selectedZone}
          enabled={false}
          loading={false}
          errorMessage={null}
          onToggle={onToggle}
        />,
      );
    });

    expect(container.textContent).toContain('전시장 A');
    expect(container.textContent).toContain('전시장 A의 대피 경로 표시');
    expect(container.textContent).not.toContain('추천');
    expect(container.querySelectorAll('[role="switch"]')).toHaveLength(1);
    expect(container.querySelectorAll('input[type="checkbox"]')).toHaveLength(0);

    act(() => container.querySelector<HTMLButtonElement>('[role="switch"]')?.click());
    expect(onToggle).toHaveBeenCalledWith(true);
  });
});
