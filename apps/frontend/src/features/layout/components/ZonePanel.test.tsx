// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { StructureConstraint } from '../api/layoutMetadataApi';
import { StructureConstraintPanel } from './ZonePanel';

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
});

function constraint(patch: Partial<StructureConstraint> = {}): StructureConstraint {
  return {
    fabricId: 20,
    zoneId: 30,
    movable: true,
    maxMovementDistance: null,
    rotationLocked: false,
    keepAgainstWall: false,
    wallContact: true,
    ...patch,
  };
}

function renderPanel(value: StructureConstraint, onChange = vi.fn(), onMovementPreview = vi.fn()) {
  act(() => {
    root.render(
      <StructureConstraintPanel
        fabricName="진열대"
        zoneName="담당 구역"
        constraint={value}
        editable
        saved
        onMovementPreview={onMovementPreview}
        onChange={onChange}
      />,
    );
  });
  return { onChange, onMovementPreview };
}

function moveSlider(value: string) {
  const slider = container.querySelector<HTMLInputElement>('input[type="range"]');
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
  act(() => {
    setter?.call(slider, value);
    slider?.dispatchEvent(new Event('input', { bubbles: true }));
  });
  act(() => slider?.dispatchEvent(new PointerEvent('pointerup', { bubbles: true })));
}

describe('StructureConstraintPanel', () => {
  it('disables and clears the wall option for a structure away from walls', () => {
    renderPanel(constraint({ wallContact: false, keepAgainstWall: true }));

    const checkboxes = container.querySelectorAll<HTMLInputElement>('input[type="checkbox"]');
    const wallCheckbox = checkboxes.item(1);
    expect(wallCheckbox.disabled).toBe(true);
    expect(wallCheckbox.checked).toBe(false);
    expect(container.textContent).toContain('벽에 닿아 있는 구조물만 설정할 수 있습니다');
  });

  it('maps fixed, bounded and free slider positions to the API patch', () => {
    const { onChange, onMovementPreview } = renderPanel(constraint());

    moveSlider('0');
    expect(onChange).toHaveBeenLastCalledWith({
      movable: false,
      clearMaxMovementDistance: true,
      maxMovementDistance: null,
    });

    moveSlider('2.5');
    expect(onChange).toHaveBeenLastCalledWith({ movable: true, maxMovementDistance: 2.5 });
    expect(onMovementPreview).toHaveBeenLastCalledWith(2.5);

    moveSlider('5.5');
    expect(onChange).toHaveBeenLastCalledWith({
      movable: true,
      clearMaxMovementDistance: true,
      maxMovementDistance: null,
    });
    expect(onMovementPreview).toHaveBeenLastCalledWith(null);
  });
});
