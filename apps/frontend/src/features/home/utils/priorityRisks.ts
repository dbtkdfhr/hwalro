import type { Risk } from '../../risks/types/risks';

export const PRIORITY_RISK_LIMIT = 3;

const RESOLVED_STATUS = '완료';

const SEVERITY_WEIGHT: Record<string, number> = {
  높음: 3,
  보통: 2,
  낮음: 1,
};

export function filterPriorityRisks(risks: Risk[]): Risk[] {
  return risks
    .filter((risk) => risk.status !== RESOLVED_STATUS)
    .slice()
    .sort((a, b) => {
      const weightDiff = (SEVERITY_WEIGHT[b.severity] ?? 0) - (SEVERITY_WEIGHT[a.severity] ?? 0);
      if (weightDiff !== 0) return weightDiff;
      const byCreatedAt = new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      return byCreatedAt !== 0 ? byCreatedAt : b.id - a.id;
    });
}

/**
 * 우선 확인할 항목: 미완료 주의 항목을 심각도 높은 순(높음 > 보통 > 낮음) 및 최신순으로 정렬하여 상한선만큼 반환.
 */
export function selectPriorityRisks(risks: Risk[], limit = PRIORITY_RISK_LIMIT): Risk[] {
  return filterPriorityRisks(risks).slice(0, limit);
}

export function countPriorityRisks(risks: Risk[]): number {
  return filterPriorityRisks(risks).length;
}
