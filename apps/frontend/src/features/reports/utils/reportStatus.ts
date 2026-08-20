import type { ReportStatus } from '../types/report';

export function isAiReportGenerating(status: ReportStatus): boolean {
  return status === 'AI 작성 중';
}

export function canRetryAiReport(status: ReportStatus): boolean {
  return status === '생성 실패';
}

export function canOpenReport(status: ReportStatus): boolean {
  return !isAiReportGenerating(status) && !canRetryAiReport(status);
}
