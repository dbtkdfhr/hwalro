// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LayerTreeRow } from './LayerTreeRow';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

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
  document.body.replaceChildren();
});

describe('LayerTreeRow context menu', () => {
  it('uses right click and keyboard without rendering an overflow button', () => {
    const group = vi.fn();
    act(() => {
      root.render(
        <ul>
          <LayerTreeRow
            label="벽 1"
            selected
            ariaLabel="벽 1 선택"
            onSelect={vi.fn()}
            menuItems={[{ label: '선택 요소를 구역으로 묶기', onSelect: group }]}
          />
        </ul>,
      );
    });
    const row = container.querySelector<HTMLButtonElement>('button');
    expect(row).not.toBeNull();
    expect(document.body.textContent).not.toContain('⋯');

    act(() => row?.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true })));
    const action = document.body.querySelector<HTMLButtonElement>('[role="menuitem"]');
    expect(action?.textContent).toContain('선택 요소를 구역으로 묶기');
    expect(document.activeElement).toBe(action);

    act(() => action?.click());
    expect(group).toHaveBeenCalledOnce();

    act(() =>
      row?.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'F10', shiftKey: true, bubbles: true }),
      ),
    );
    expect(document.body.querySelector('[role="menu"]')).not.toBeNull();
  });

  it('keeps the native context menu when no custom actions exist', () => {
    act(() => {
      root.render(
        <ul>
          <LayerTreeRow
            label="비상구 1"
            selected={false}
            ariaLabel="비상구 1 선택"
            onSelect={vi.fn()}
          />
        </ul>,
      );
    });
    const event = new MouseEvent('contextmenu', { bubbles: true, cancelable: true });
    const row = container.querySelector<HTMLButtonElement>('button');

    expect(row?.dispatchEvent(event)).toBe(true);
    expect(document.body.querySelector('[role="menu"]')).toBeNull();
  });
});
