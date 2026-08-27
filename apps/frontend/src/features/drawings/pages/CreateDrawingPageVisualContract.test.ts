import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('도면 등록 페이지 시각 계약', () => {
  it('건물 3D 뷰에서 연결된 층을 선택할 수 있다', () => {
    const source = readFileSync(new URL('./CreateDrawingPage.tsx', import.meta.url), 'utf8');

    expect(source).toContain('createBuildingScene(host');
    expect(source).toContain('selectableIds: LINKED_FLOOR_IDS');
    expect(source).toContain("startWithLinkedFloor('B2')");
    expect(source).toContain('role="img"');
  });

  it('3D 선택이 어려운 경우에도 버튼과 빈 도면 시작을 제공한다', () => {
    const source = readFileSync(new URL('./CreateDrawingPage.tsx', import.meta.url), 'utf8');

    expect(source).toContain('지하 2층(B2)으로 시작');
    expect(source).toContain('빈 도면으로 시작');
    expect(source).toContain('prefers-reduced-motion: reduce');
    expect(source).toContain('aria-live="polite"');
  });
});
