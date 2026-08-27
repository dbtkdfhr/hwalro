// @vitest-environment happy-dom

import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LayoutSearchStartDialog } from './LayoutSearchStartDialog';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe('LayoutSearchStartDialog', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  it('실측 확인 선택값을 탐색 시작 요청에 전달한다', async () => {
    const onStart = vi.fn();
    await act(async () => {
      root.render(
        <LayoutSearchStartDialog
          open
          drawingTitle="테스트 도면"
          starting={false}
          errorMessage={null}
          onClose={() => undefined}
          onEditConstraints={() => undefined}
          onStart={onStart}
        />,
      );
    });

    const verify = container.querySelector<HTMLInputElement>('input[type="checkbox"]');
    const start = [...container.querySelectorAll('button')].find((button) =>
      button.textContent?.includes('배치 개선안 탐색 시작'),
    );
    await act(async () => {
      verify?.click();
      start?.click();
    });

    expect(onStart).toHaveBeenCalledWith(true);
  });
});
