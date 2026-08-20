import { AxiosError } from 'axios';
import type { InspectionHistory, InspectionResult } from './types';

export const RESULT_LABELS: Record<InspectionResult, string> = {
  PENDING: '대기',
  PASS: '적합',
  REVIEW_REQUIRED: '확인 필요',
  FAIL: '부적합',
};

export const RESULT_STYLES: Record<InspectionResult, string> = {
  PENDING: 'border-line bg-surface text-text-muted',
  PASS: 'border-primary/15 bg-primary-soft text-primary',
  REVIEW_REQUIRED: 'border-orange-200 bg-orange-50 text-orange-600',
  FAIL: 'border-red-200 bg-danger-soft text-danger',
};

export function formatInspectionDate(value: string | null): string {
  if (!value) return '점검 이력 없음';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '날짜 정보 없음';
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function getInspectionSummary(inspection: InspectionHistory): string {
  if (inspection.failCount > 0) return `부적합 ${inspection.failCount}건`;
  if (inspection.reviewRequiredCount > 0) return `확인 필요 ${inspection.reviewRequiredCount}건`;
  if (inspection.completedItemCount < inspection.totalItemCount) return '점검 진행 중';
  return '전체 적합';
}

export function getSafetyCheckError(
  error: unknown,
  fallback = '안전 점검 정보를 불러오지 못했습니다.',
): string {
  if (error instanceof AxiosError) {
    return (error.response?.data as { message?: string } | undefined)?.message ?? fallback;
  }
  return fallback;
}
