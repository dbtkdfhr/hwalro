import type { SearchCandidate } from '../api/layoutSearchApi';
import { candidateResultLabel, recommendationLabel } from '../utils/searchLabels';

const MAX_TABS = 20;

interface CandidateTabsProps {
  recommended: SearchCandidate[];
  comparisons: SearchCandidate[];
  activeKey: string;
  onSelect: (key: string) => void;
}

export function getCandidateTabColorClass(operatorType: string): string {
  switch (operatorType) {
    case 'CLEAR_CORRIDOR':
    case 'OPEN_DUAL_GAP':
      return 'is-corridor';
    case 'RELIEVE_HOTSPOT':
    case 'RELIEVE_DIAGONAL':
      return 'is-hotspot';
    case 'REBALANCE_EXIT':
    case 'EXIT_OPENING':
    case 'CLEAR_EXIT_PATH':
      return 'is-exit';
    case 'ROTATE_TO_OPEN':
      return 'is-rotate';
    default:
      return 'is-improved';
  }
}

function tabs(candidates: SearchCandidate[], activeKey: string, onSelect: (key: string) => void) {
  return candidates.map((candidate) => {
    const key = `c-${candidate.candidateId}`;
    const colorClass = getCandidateTabColorClass(candidate.operatorType);
    const isSelected = key === activeKey;
    const isRecommended = (candidate.recommendationTypes?.length ?? 0) > 0;
    return (
      <button
        key={key}
        type="button"
        role="tab"
        id={`candidate-tab-${key}`}
        aria-selected={isSelected}
        aria-controls="candidate-tabpanel"
        className={`no-improvement-tab ${colorClass}${isSelected ? ' is-active' : ''}`}
        onClick={() => onSelect(key)}
        title={`후보 #${candidate.candidateId} · ${candidateResultLabel(candidate)} · ${recommendationLabel(candidate.recommendationTypes)}`}
      >
        <span>
          {isRecommended ? '추천' : '후보'} #{candidate.candidateId}
        </span>
        <span className="candidate-tab__mark">
          {recommendationLabel(candidate.recommendationTypes)}
        </span>
      </button>
    );
  });
}

export function CandidateTabs({
  recommended,
  comparisons,
  activeKey,
  onSelect,
}: CandidateTabsProps) {
  const visibleRecommended = recommended.slice(0, 3);
  const remaining = MAX_TABS - visibleRecommended.length;
  const visibleComparisons = comparisons.slice(0, remaining);
  const hiddenCount =
    recommended.length + comparisons.length - visibleRecommended.length - visibleComparisons.length;
  return (
    <div className="no-improvement-tabs" role="tablist" aria-label="개선 후보">
      {visibleRecommended.length > 0 && (
        <span className="candidate-tab-group-label">추천 개선안</span>
      )}
      {tabs(visibleRecommended, activeKey, onSelect)}
      {visibleComparisons.length > 0 && (
        <span className="candidate-tab-group-label">실측 비교 기록</span>
      )}
      {tabs(visibleComparisons, activeKey, onSelect)}
      {hiddenCount > 0 && (
        <span className="no-improvement-tab no-improvement-tab__overflow" aria-hidden="true">
          +{hiddenCount}
        </span>
      )}
    </div>
  );
}
