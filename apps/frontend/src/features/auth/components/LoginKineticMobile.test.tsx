// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LoginKineticMobile from './LoginKineticMobile';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

interface MediaPreferences {
  desktop?: boolean;
  forcedColors?: boolean;
  reducedMotion?: boolean;
}

function installMatchMedia({
  desktop = true,
  forcedColors = false,
  reducedMotion = false,
}: MediaPreferences = {}) {
  vi.stubGlobal(
    'matchMedia',
    vi.fn((query: string) => {
      const matches = query.includes('min-width')
        ? desktop
        : query.includes('reduced-motion')
          ? reducedMotion
          : forcedColors;

      return {
        matches,
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(() => true),
      } satisfies MediaQueryList;
    }),
  );
}

describe('LoginKineticMobile', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    container.remove();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  async function renderMobile() {
    await act(async () => {
      root.render(<LoginKineticMobile />);
    });
  }

  async function unmountMobile() {
    await act(async () => {
      root.unmount();
    });
  }

  it.each([
    ['모바일', { desktop: false }],
    ['모션 감소', { reducedMotion: true }],
    ['강제 색상', { forcedColors: true }],
  ] as const)('%s 환경에서는 Three 장면을 예약하지 않는다', async (_label, preferences) => {
    installMatchMedia(preferences);
    const requestIdleCallback = vi.fn(() => 1);
    vi.stubGlobal('requestIdleCallback', requestIdleCallback);

    await renderMobile();

    expect(requestIdleCallback).not.toHaveBeenCalled();
    expect(container.querySelector('.login-kinetic-mobile')?.getAttribute('aria-hidden')).toBe(
      'true',
    );
    expect(container.querySelector('.login-kinetic-mobile__fallback')).not.toBeNull();
    expect(container.querySelector('.login-kinetic-mobile__route')).not.toBeNull();
    expect(container.querySelector('.login-kinetic-mobile__exit')).not.toBeNull();

    await unmountMobile();
  });

  it('데스크톱 장면의 유휴 예약을 언마운트할 때 취소한다', async () => {
    installMatchMedia();
    const requestIdleCallback = vi.fn(() => 17);
    const cancelIdleCallback = vi.fn();
    vi.stubGlobal('requestIdleCallback', requestIdleCallback);
    vi.stubGlobal('cancelIdleCallback', cancelIdleCallback);

    await renderMobile();

    expect(requestIdleCallback).toHaveBeenCalledWith(expect.any(Function), { timeout: 1200 });

    await unmountMobile();

    expect(cancelIdleCallback).toHaveBeenCalledWith(17);
  });
});
