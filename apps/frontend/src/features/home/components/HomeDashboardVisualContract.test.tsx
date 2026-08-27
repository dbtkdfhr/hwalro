import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { PriorityRiskPanel } from './PriorityRiskPanel';
import { WorkStatusCards } from './WorkStatusCards';

describe('home dashboard visual contract', () => {
  it('업무 현황을 장식 점 없이 숫자 중심으로 표시한다', () => {
    const html = renderToStaticMarkup(
      <WorkStatusCards
        summary={{ inProgressCount: 4, completedThisWeekCount: 7 }}
        isPending={false}
        isError={false}
        errorMessage=""
        onRetry={vi.fn()}
      />,
    );

    expect(html).not.toContain('aria-hidden="true"');
    expect(html).toContain('시뮬레이션 처리 중');
    expect(html).toContain('이번 주 완료');
  });

  it('업무 현황 로딩 상태가 최종 레이아웃과 같은 스켈레톤을 사용한다', () => {
    const html = renderToStaticMarkup(
      <WorkStatusCards
        summary={{ inProgressCount: 0, completedThisWeekCount: 0 }}
        isPending
        isError={false}
        errorMessage=""
        onRetry={vi.fn()}
      />,
    );

    expect(html).toContain('aria-busy="true"');
    expect(html).toContain('home-work-status__skeleton');
  });

  it('위험도는 배지 하나로만 강조한다', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <PriorityRiskPanel
          items={[
            {
              id: 1,
              title: '출구 앞 적치물 확인',
              severity: '높음',
              status: '검토 중',
              assigneeId: null,
              assigneeName: null,
            },
          ]}
          totalCount={1}
          isPending={false}
          isError={false}
          errorMessage=""
          onRetry={vi.fn()}
        />
      </MemoryRouter>,
    );

    expect(html).toContain('bg-danger/15');
    expect(html).not.toContain('border-l-danger');
  });

  it('하단 정보량과 모바일 진행 단계에 맞춘 레이아웃을 사용한다', () => {
    const styles = readFileSync(new URL('../home.css', import.meta.url), 'utf8').replace(
      /\r\n/g,
      '\n',
    );

    expect(styles).toContain('grid-template-columns: minmax(18rem, 0.72fr) minmax(0, 1.28fr);');
    expect(styles).toContain('.home-review-scene__station {\n    width: 3rem;');
    expect(styles).toContain(':focus-visible');
  });
});
