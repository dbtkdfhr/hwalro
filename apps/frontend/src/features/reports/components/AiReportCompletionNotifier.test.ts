import { describe, expect, it } from 'vitest';
import type { AiReportDraftMonitorItem, ReportStatus } from '../types/report';
import { findNewlyCompletedAiReports } from './AiReportCompletionNotifier';

function report(id: number, status: ReportStatus): AiReportDraftMonitorItem {
  return { id, title: `AI 보고서 ${id}`, status };
}

describe('findNewlyCompletedAiReports', () => {
  it('이번 조회에서 처음 초안 상태가 된 AI 보고서만 반환한다', () => {
    const previous = new Map<number, ReportStatus>([
      [1, 'AI 작성 중'],
      [2, '초안'],
      [3, '생성 실패'],
    ]);
    const current = [
      report(1, '초안'),
      report(2, '초안'),
      report(3, '생성 실패'),
      report(4, '초안'),
    ];

    expect(findNewlyCompletedAiReports(previous, current).map(({ id }) => id)).toEqual([1, 4]);
  });
});
