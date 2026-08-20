import type { SimulationExecutionStatus } from '../../simulations/types';
import type { LastActivity, LastActivityType, ReviewStep, ReviewStepState } from '../types/home';

const STEP_LABELS: Array<{ key: ReviewStep['key']; label: string }> = [
  { key: 'LAYOUT', label: '도면 배치' },
  { key: 'SIMULATION_LAYOUT', label: '시뮬레이션 배치' },
  { key: 'SETUP', label: '시뮬레이션 설정' },
  { key: 'ANALYSIS', label: '결과 분석' },
];

export interface ReviewProgressInput {
  /** 아직 시뮬레이션을 만들지 않았으면 null. */
  status: SimulationExecutionStatus | null;
  /** 배치된 에이전트 수. 0이면 아직 시뮬레이션 배치를 하지 않은 것으로 본다. */
  totalPeople: number;
  /** 결과 분석 화면을 실제로 열었는지. */
  analysisOpened: boolean;
}

/**
 * 마지막 작업 종류에 대응하는 화면 경로.
 * 경로는 router/index.tsx의 정의와 일치해야 한다.
 */
export function resumePath(activity: LastActivity): string {
  switch (activity.activityType) {
    case 'LAYOUT_EDIT':
      return `/layout/${activity.resourceId}`;
    case 'SIMULATION_SETUP':
      return `/simulations/${activity.resourceId}/setup`;
    case 'SIMULATION_RESULT':
      return `/simulations/${activity.resourceId}/results`;
  }
}

/**
 * 시뮬레이션 상태에서 스텝퍼 진행도를 파생한다.
 * 진행도는 별도로 저장하지 않고 항상 조회한 값에서 계산한다.
 *
 * 시뮬레이션 배치와 시뮬레이션 설정은 같은 화면에서 이루어지므로
 * 에이전트 배치 여부(totalPeople)로 두 단계를 구분한다.
 */
function stepStates({
  status,
  totalPeople,
  analysisOpened,
}: ReviewProgressInput): ReviewStepState[] {
  switch (status) {
    case null:
      return ['current', 'upcoming', 'upcoming', 'upcoming'];
    case 'DRAFT':
      return totalPeople > 0
        ? ['done', 'done', 'current', 'upcoming']
        : ['done', 'current', 'upcoming', 'upcoming'];
    case 'REQUESTED':
    case 'RUNNING':
    case 'FAILED':
    case 'CANCELLED':
      return ['done', 'done', 'done', 'current'];
    case 'COMPLETED':
      return analysisOpened
        ? ['done', 'done', 'done', 'done']
        : ['done', 'done', 'done', 'current'];
  }
}

export function reviewSteps(input: ReviewProgressInput): ReviewStep[] {
  const states = stepStates(input);
  return STEP_LABELS.map((step, index) => ({ ...step, state: states[index] }));
}

export function currentStageLabel({
  status,
  totalPeople,
  analysisOpened,
}: ReviewProgressInput): string {
  switch (status) {
    case null:
      return '도면 배치 중';
    case 'DRAFT':
      return totalPeople > 0 ? '시뮬레이션 설정 중' : '시뮬레이션 배치 중';
    case 'REQUESTED':
    case 'RUNNING':
      return '시뮬레이션 실행 중';
    case 'COMPLETED':
      return analysisOpened ? '결과 분석 중' : '결과 분석 대기';
    case 'FAILED':
      return '실행 실패';
    case 'CANCELLED':
      return '실행 취소';
  }
}

/** 마지막 작업이 도면 편집이면 시뮬레이션 상태가 없다. */
export function activityHasSimulation(activityType: LastActivityType): boolean {
  return activityType !== 'LAYOUT_EDIT';
}
