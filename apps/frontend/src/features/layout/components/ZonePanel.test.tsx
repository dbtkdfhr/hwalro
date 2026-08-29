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
    movementPolicy: 'WITHIN_ZONE',
    ...patch,
  };
}

function renderPanel(
  value: StructureConstraint,
  onChange = vi.fn(),
  zoneName: string | null = '담당 구역',
) {
  act(() => {
    root.render(
      <StructureConstraintPanel
        fabricName="진열대"
        zoneName={zoneName}
        constraint={value}
        editable
        saved
        onChange={onChange}
      />,
    );
  });
  return { onChange };
}

describe('StructureConstraintPanel', () => {
  it('shows the three movement levels with within-zone selected by default', () => {
    renderPanel(constraint());

    const radios = container.querySelectorAll<HTMLInputElement>('input[type="radio"]');
    expect(radios).toHaveLength(3);
    expect(container.textContent).toContain('자유 이동');
    expect(container.textContent).toContain('구역 내에서 이동');
    expect(container.textContent).toContain('이동 불가');
    expect(radios.item(1).checked).toBe(true);
  });

  it('sends the selected movement policy', () => {
    const { onChange } = renderPanel(constraint());
    const fixed = container.querySelector<HTMLInputElement>('input[value="FIXED"]');

    act(() => fixed?.click());

    expect(onChange).toHaveBeenLastCalledWith({ movementPolicy: 'FIXED' });
  });

  it('warns that within-zone movement needs a zone membership', () => {
    renderPanel(constraint(), vi.fn(), null);

    expect(container.textContent).toContain('소속 구역이 없어 현재 위치에서 이동하지 않습니다');
  });
});
