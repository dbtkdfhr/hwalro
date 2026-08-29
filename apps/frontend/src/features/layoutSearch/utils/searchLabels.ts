import type {
  CandidateStatus,
  MetricDelta,
  RecommendationType,
  SearchStatus,
} from '../api/layoutSearchApi';

export const SEARCH_STATUS_LABELS: Record<SearchStatus, string> = {
  PENDING: '준비 중',
  DIAGNOSING: '진단 중',
  GENERATING: '후보 생성 중',
  VERIFYING: '검증 중',
  COMPLETED: '완료',
  NO_IMPROVEMENT: '개선 후보 없음',
  FAILED: '실패',
  CANCELLED: '취소됨',
};

export const CANDIDATE_STATUS_LABELS: Record<CandidateStatus, string> = {
  GENERATED: '생성됨',
  REJECTED_CONSTRAINT: '제약 위반',
  QUEUED: '대기 중',
  RUNNING: '검증 중',
  EVALUATED: '개선 확인',
  NOT_IMPROVED: '개선 미달',
  FAILED: '검증 실패',
};

export const OPERATOR_LABELS: Record<string, string> = {
  CLEAR_CORRIDOR: '통로 확보',
  RELIEVE_HOTSPOT: '혼잡 완화',
  RELIEVE_DIAGONAL: '혼잡 완화(대각)',
  REBALANCE_EXIT: '출구 균형',
  ROTATE_TO_OPEN: '회전 개방',
  OPEN_DUAL_GAP: '양쪽 통로 확보',
  EXIT_OPENING: '출구 전면 확보',
  CLEAR_EXIT_PATH: '출구 직선 경로 확보',
  CONSTRAINT: '제약 위반',
  BOUNDARY_DOCKING: '경계 흡착 배치',
};

export const FINDING_LABELS: Record<string, string> = {
  BOTTLENECK: '병목 구역',
  CONGESTION_HOTSPOT: '혼잡 구역',
  EXIT_IMBALANCE: '출구 편중',
  EVACUATION_TAIL: '대피 지연',
  IDEAL_ROUTE: '이상 경로',
};

export const RECOMMENDATION_LABELS: Record<RecommendationType, string> = {
  TOTAL_TIME: '총시간 최적',
  AVERAGE_TIME: '평균시간 최적',
  BALANCED: '균형 최적',
  GEOMETRY: '실측 전 예상안',
};

export const RECOMMENDATION_TYPE_META: Record<
  RecommendationType,
  { label: string; badgeStyle: string }
> = {
  TOTAL_TIME: {
    label: '총시간 최적',
    badgeStyle: 'bg-indigo-50 text-indigo-700 border border-indigo-200/80',
  },
  AVERAGE_TIME: {
    label: '평균시간 최적',
    badgeStyle: 'bg-teal-50 text-teal-800 border border-teal-200/80',
  },
  BALANCED: {
    label: '균형 최적',
    badgeStyle: 'bg-amber-50 text-amber-800 border border-amber-200/80',
  },
  GEOMETRY: {
    label: '실측 전 예상안',
    badgeStyle: 'bg-soft-gray text-text-muted border border-line',
  },
};

export function recommendationLabel(types: readonly RecommendationType[] | undefined) {
  return types && types.length > 0
    ? types.map((type) => RECOMMENDATION_LABELS[type]).join(' · ')
    : '비교 후보';
}

export function candidateResultLabel(candidate: { status: CandidateStatus; delta: MetricDelta[] }) {
  if (candidate.status === 'EVALUATED') return '개선됨';
  if (candidate.status === 'FAILED') return '검증 실패';
  if (candidate.status !== 'NOT_IMPROVED') return CANDIDATE_STATUS_LABELS[candidate.status];
  const evacuationDeltas = candidate.delta.filter((item) =>
    ['TOTAL_EVACUATION_TIME_SECONDS', 'AVERAGE_EVACUATION_TIME_SECONDS'].includes(item.metricType),
  );
  if (evacuationDeltas.some((item) => item.difference > 0)) return '악화됨';
  if (evacuationDeltas.length > 0 && evacuationDeltas.every((item) => item.difference === 0)) {
    return '변화 없음';
  }
  return '개선 미달';
}

export function candidateResultBadgeStyle(candidate: {
  status: CandidateStatus;
  delta: MetricDelta[];
}): string {
  const label = candidateResultLabel(candidate);
  if (label === '개선됨' || label === '개선 확인') {
    return 'bg-success-soft text-success-strong border border-success/25';
  }
  if (label === '검증 실패' || label === '제약 위반' || label === '악화됨') {
    return 'bg-danger-soft text-danger-strong border border-danger/25';
  }
  if (candidate.status === 'RUNNING') {
    return 'bg-primary-soft text-primary-active border border-primary/25';
  }
  return 'bg-soft-gray text-text-muted border border-line';
}

export const REJECT_REASON_LABELS: Record<string, string> = {
  OUTSIDE_BOUNDARY: '도면 경계를 벗어남',
  OVERLAP: '다른 구조물과 겹침',
  CORRIDOR_BLOCKED: '통로가 막힘',
  AGENT_UNREACHABLE_EXIT: '대피 경로 단절',
  INVALID_GEOMETRY: '좌표가 유효하지 않음',
  CONSTRAINT_FIXED: '고정된 구조물',
  CONSTRAINT_ZONE: '금지 영역과 겹침',
  CONSTRAINT_WALL_ANCHOR: '벽면 접촉을 벗어남',
};

export const METRIC_LABELS: Record<string, string> = {
  SIMULATION_DURATION_SECONDS: '시뮬레이션 시간',
  TOTAL_EVACUATION_TIME_SECONDS: '총 대피 시간',
  AVERAGE_EVACUATION_TIME_SECONDS: '평균 대피 시간',
  EVACUATED_PEOPLE: '대피 완료 인원',
  REMAINING_PEOPLE: '남은 인원',
  MAX_DENSITY: '최대 밀집도',
};

export function metricLabel(metricType: string) {
  return METRIC_LABELS[metricType] ?? metricType;
}

export function operatorLabel(operatorType: string) {
  return OPERATOR_LABELS[operatorType] ?? operatorType;
}

export function findingLabel(findingType: string) {
  return FINDING_LABELS[findingType] ?? findingType;
}

export function rejectReasonLabel(reason: string | null) {
  if (!reason) {
    return null;
  }
  if (reason.startsWith('AGENT_PLACEMENT_FAILED')) {
    return '변경 배치에서 초기 인원을 안전하게 배치할 공간이 부족합니다.';
  }
  if (reason.startsWith('SEARCH_CANCELLED')) {
    return '사용자가 배치 개선안 탐색을 취소했습니다.';
  }
  return REJECT_REASON_LABELS[reason] ?? reason;
}

export function formatDuration(totalSeconds: number) {
  if (totalSeconds < 60) {
    return `약 ${Math.max(1, Math.round(totalSeconds))}초`;
  }
  const minutes = Math.round(totalSeconds / 60);
  if (minutes < 60) {
    return `약 ${minutes}분`;
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest > 0 ? `약 ${hours}시간 ${rest}분` : `약 ${hours}시간`;
}

export function formatNumber(value: number) {
  return value.toLocaleString('ko-KR', { maximumFractionDigits: 2 });
}

export function formatDelta(delta: MetricDelta) {
  const percent = Math.round(delta.ratio * 1000) / 10;
  const sign = delta.difference > 0 ? '+' : '';
  return `${sign}${formatNumber(delta.difference)} (${sign}${percent}%)`;
}
