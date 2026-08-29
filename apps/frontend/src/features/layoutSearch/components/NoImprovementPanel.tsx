import type { SearchCandidate } from '../api/layoutSearchApi';
import {
  candidateResultLabel,
  hasOfficialRecommendation,
  recommendationLabel,
} from '../utils/searchLabels';

const MAX_TABS = 20;

interface CandidateTabsProps {
  recommended: SearchCandidate[];
  comparisons: SearchCandidate[];
  activeKey: string;
  onSelect: (key: string) => void;
}

export function getCandidateTabColorClass(_operatorType: string): string {
  return 'is-improved';
}

function tabs(
  candidates: SearchCandidate[],
  activeKey: string,
  onSelect: (key: string) => void,
) {
  return candidates.map((candidate) => {
    const key = `c-${candidate.candidateId}`;
    const colorClass = getCandidateTabColorClass(candidate.operatorType);
    const isSelected = key === activeKey;
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
        title={`${candidateResultLabel(candidate)} · ${recommendationLabel(candidate.recommendationTypes)}`}
      >
        {candidateResultLabel(candidate)}
        <span className="candidate-tab__mark">
          {recommendationLabel(candidate.recommendationTypes)}
        </span>
      </button>
    );
  });
}

export function CandidateTabs({ recommended, comparisons, activeKey, onSelect }: CandidateTabsProps) {
  const officialRecommendations = recommended.filter((candidate) =>
    hasOfficialRecommendation(candidate.recommendationTypes),
  );
  const comparisonRecords = [
    ...recommended.filter((candidate) => !hasOfficialRecommendation(candidate.recommendationTypes)),
    ...comparisons,
  ];
  const visibleRecommended = officialRecommendations.slice(0, 3);
  const remaining = MAX_TABS - visibleRecommended.length;
  const visibleComparisons = comparisonRecords.slice(0, remaining);
  const hiddenCount =
    officialRecommendations.length + comparisonRecords.length - visibleRecommended.length - visibleComparisons.length;
  return (
    <div className="no-improvement-tabs" role="tablist" aria-label="개선 후보">
      {visibleRecommended.length > 0 && <span className="candidate-tab-group-label">추천 개선안</span>}
      {tabs(visibleRecommended, activeKey, onSelect)}
      {visibleComparisons.length > 0 && <span className="candidate-tab-group-label">실측 비교 기록</span>}
      {tabs(visibleComparisons, activeKey, onSelect)}
      {hiddenCount > 0 && (
        <span className="no-improvement-tab no-improvement-tab__overflow" aria-hidden="true">
          +{hiddenCount}
        </span>
      )}
    </div>
  );
}
