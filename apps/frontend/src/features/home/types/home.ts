export type LastActivityType = 'LAYOUT_EDIT' | 'SIMULATION_SETUP' | 'SIMULATION_RESULT';

/** auth-service가 보관하는 마지막 작업 포인터. 표시값은 포함하지 않는다. */
export interface LastActivity {
  activityType: LastActivityType;
  resourceId: number;
  occurredAt: string;
}

export interface SimulationWorkSummary {
  inProgressCount: number;
  completedThisWeekCount: number;
}

/**
 * 검토 파이프라인 4단계.
 * SCENARIO.md의 "도면 배치 및 저장 → 위험구역, 사람 배치 및 저장 → 시뮬레이션 실행 → 결과 확인" 흐름을 따른다.
 */
export type ReviewStepKey = 'LAYOUT' | 'SIMULATION_LAYOUT' | 'SETUP' | 'ANALYSIS';

export type ReviewStepState = 'done' | 'current' | 'upcoming';

export interface ReviewStep {
  key: ReviewStepKey;
  label: string;
  state: ReviewStepState;
}

/** 마지막 작업 포인터 + 조회한 표시값을 합친 화면 전용 상태. */
export interface ActiveReview {
  title: string;
  resumePath: string;
  subtitle: string;
  occurredAt: string;
  currentStageLabel: string;
  steps: ReviewStep[];
}

export interface PriorityRiskItem {
  id: number;
  title: string;
  severity: string;
  status: string;
  assigneeId: number | null;
  assigneeName: string | null;
}
