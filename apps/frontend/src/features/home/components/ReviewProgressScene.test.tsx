// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { ReviewStep } from '../types/home';
import { ReviewProgressScene } from './ReviewProgressScene';
import { ReviewProgressStepper } from './ReviewProgressStepper';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const STEPS: ReviewStep[] = [
  { key: 'LAYOUT', label: '도면 배치', state: 'done' },
  { key: 'SIMULATION_LAYOUT', label: '시뮬레이션 배치', state: 'current' },
  { key: 'SETUP', label: '시뮬레이션 설정', state: 'upcoming' },
  { key: 'ANALYSIS', label: '결과 분석', state: 'upcoming' },
];

describe('ReviewProgressScene', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  async function render(element: React.ReactNode) {
    await act(async () => root.render(element));
  }

  it('2D 경로와 네 단계 상태를 함께 표시한다', async () => {
    await render(<ReviewProgressStepper steps={STEPS} />);

    expect(container.querySelector('.home-review-scene__route')).not.toBeNull();
    expect(container.querySelector('.home-review-scene__canvas')).toBeNull();
    expect(container.querySelectorAll('.home-review-scene__station')).toHaveLength(4);
    expect(container.querySelector('[aria-current="step"]')?.textContent).toContain(
      '시뮬레이션 배치',
    );
    expect(container.querySelector('[aria-current="step"]')?.textContent).toContain('진행 중');
  });

  it('업무 단계가 완료될수록 활성 경로의 클립 영역을 늘린다', async () => {
    await render(<ReviewProgressScene steps={STEPS} />);
    expect(container.querySelector('clipPath rect')?.getAttribute('width')).toBe('276');

    await render(<ReviewProgressScene steps={STEPS.map((step) => ({ ...step, state: 'done' }))} />);
    expect(container.querySelector('clipPath rect')?.getAttribute('width')).toBe('676');
  });
});
