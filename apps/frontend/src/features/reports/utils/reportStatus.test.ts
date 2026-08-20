import { describe, expect, it } from 'vitest';
import type { ReportStatus } from '../types/report';
import { canOpenReport, isAiReportGenerating, canRetryAiReport } from './reportStatus';

describe('report status behavior', () => {
  it.each(['초안', '작성 중', '완료'] satisfies ReportStatus[])(
    '%s 보고서는 열 수 있다',
    (status) => {
      expect(canOpenReport(status)).toBe(true);
    },
  );

  it.each(['AI 작성 중', '생성 실패'] satisfies ReportStatus[])(
    '%s 보고서는 열 수 없다',
    (status) => {
      expect(canOpenReport(status)).toBe(false);
    },
  );

  it('AI 작성 중 상태만 목록 자동 갱신 대상으로 본다', () => {
    expect(isAiReportGenerating('AI 작성 중')).toBe(true);
    expect(isAiReportGenerating('초안')).toBe(false);
  });

  it('생성 실패 상태만 재시도할 수 있다', () => {
    expect(canRetryAiReport('생성 실패')).toBe(true);
    expect(canRetryAiReport('AI 작성 중')).toBe(false);
  });
});
