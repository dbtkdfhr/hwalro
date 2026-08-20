import type { LayoutSearch } from '../api/layoutSearchApi';
import { isActiveSearchStatus } from '../hooks/useLayoutSearch';
import { formatDuration, formatNumber, SEARCH_STATUS_LABELS } from '../utils/searchLabels';

export const PHASE_MESSAGES: Record<LayoutSearch['status'], string> = {
  PENDING: '탐색 작업을 준비하고 있습니다',
  DIAGNOSING: '시뮬레이션 결과를 진단하고 있습니다',
  GENERATING: '검증할 배치 후보를 생성하고 있습니다',
  VERIFYING: '실제 엔진으로 후보를 검증하고 있습니다',
  COMPLETED: '배치 개선안 탐색 완료',
  NO_IMPROVEMENT: '개선안 탐색 종료',
  FAILED: '배치 개선안 탐색 실패',
  CANCELLED: '배치 개선안 탐색 취소됨',
};

interface Props {
  search: LayoutSearch;
  onCancel: () => void;
  cancelling: boolean;
  onBackToResult?: () => void;
  onRerun: () => void;
  rerunning: boolean;
}

export function SearchProgressHeader({
  search,
  onCancel,
  cancelling,
  onBackToResult,
  onRerun,
  rerunning,
}: Props) {
  const { progress } = search;
  const active = isActiveSearchStatus(search.status);
  return (
    <aside className="search-progress-floating-bar" aria-label="탐색 진행 상태">
      <div className="search-progress-floating__info">
        {onBackToResult && (
          <button
            type="button"
            className="search-progress-floating__btn search-back-button"
            onClick={onBackToResult}
          >
            ← 결과 화면
          </button>
        )}
        <span
          className={`search-progress-floating__status search-status-badge is-${search.status.toLowerCase()}`}
        >
          {SEARCH_STATUS_LABELS[search.status]}
        </span>
        <span className="search-progress-floating__meta">
          {search.failureMessage ? (
            <span className="search-progress-floating__failure-text">{search.failureMessage}</span>
          ) : active && progress.plannedCount === null ? (
            <span>진행 {formatNumber(progress.verifiedCount)}건 검증 · 전체 후보 계산 중</span>
          ) : active && progress.plannedCount !== 0 ? (
            <span>
              진행 {formatNumber(progress.verifiedCount)}/{formatNumber(progress.plannedCount ?? 0)}
            </span>
          ) : (
            <span>{PHASE_MESSAGES[search.status]}</span>
          )}
          {active &&
            progress.estimatedRemainingSeconds !== null &&
            progress.estimatedRemainingSeconds > 0 && (
              <span> · 남은 시간 {formatDuration(progress.estimatedRemainingSeconds)}</span>
            )}
        </span>
      </div>
      <div className="search-progress-floating__actions">
        {active && (
          <button
            type="button"
            className="search-progress-floating__btn search-progress-floating__btn--cancel search-cancel-button"
            disabled={cancelling}
            onClick={onCancel}
          >
            {cancelling ? '취소 요청 중' : '탐색 취소'}
          </button>
        )}
        {!active && (
          <button
            type="button"
            className="search-progress-floating__btn search-rerun-button"
            disabled={rerunning}
            onClick={onRerun}
          >
            {rerunning ? '준비 중' : '제약 설정 다시 열기'}
          </button>
        )}
      </div>
    </aside>
  );
}
