import type { MetricDelta, SearchCandidate } from '../api/layoutSearchApi';
import { CANDIDATE_STATUS_LABELS, formatDelta, operatorLabel } from '../utils/searchLabels';

export function primaryDelta(candidate: SearchCandidate): MetricDelta | null {
  return (
    candidate.delta.find((item) => item.metricType === 'TOTAL_EVACUATION_TIME_SECONDS') ??
    candidate.delta.find((item) => item.metricType === 'REMAINING_PEOPLE') ??
    candidate.delta[0] ??
    null
  );
}

interface Props {
  candidates: SearchCandidate[];
  selectedCandidateId: number | null;
  onSelect: (candidateId: number) => void;
}

export function CandidateList({ candidates, selectedCandidateId, onSelect }: Props) {
  if (candidates.length === 0) {
    return (
      <aside className="search-options" aria-label="개선 후보 목록">
        <p className="proposal-shortage">검증된 개선 후보가 아직 없습니다.</p>
      </aside>
    );
  }
  return (
    <aside className="search-options" aria-label="개선 후보 목록">
      <div className="workspace-section-heading">
        <span>개선 후보</span>
        <small>실측 검증 순서</small>
      </div>
      {candidates.map((candidate) => {
        const delta = primaryDelta(candidate);
        return (
          <div
            key={candidate.candidateId}
            className={`proposal-option ${candidate.candidateId === selectedCandidateId ? 'is-selected' : ''}`}
          >
            <button type="button" onClick={() => onSelect(candidate.candidateId)}>
              <strong>
                {operatorLabel(candidate.operatorType)} ·{' '}
                {candidate.round === 1 ? '1차' : `${candidate.round}차`}
              </strong>
              <small>
                {candidate.preparedSimulation !== null
                  ? '시뮬레이션 준비됨'
                  : CANDIDATE_STATUS_LABELS[candidate.status]}
              </small>
              {delta && <em className="delta-badge is-improved">{formatDelta(delta)}</em>}
            </button>
          </div>
        );
      })}
    </aside>
  );
}
