import type { SimulationExecutionStatus } from '../types';

export const STATUS_LABELS: Record<SimulationExecutionStatus, string> = {
  DRAFT: '배치 작성 중',
  REQUESTED: '실행 대기',
  RUNNING: '실행 중',
  COMPLETED: '실행 완료',
  FAILED: '실행 실패',
  CANCELLED: '실행 취소',
};

export const STATUS_STYLES: Record<SimulationExecutionStatus, string> = {
  DRAFT: 'bg-soft-gray text-text-strong',
  REQUESTED: 'bg-warning-soft text-warning-strong',
  RUNNING: 'bg-primary-soft text-primary-active',
  COMPLETED: 'bg-success-soft text-success-strong',
  FAILED: 'bg-danger-soft text-danger-strong',
  CANCELLED: 'bg-soft-gray text-text-muted',
};
