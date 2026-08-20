import { Info, Lock } from 'lucide-react';
import type { SearchCandidate } from '../api/layoutSearchApi';
import {
  formatDelta,
  formatNumber,
  findingLabel,
  metricLabel,
  operatorLabel,
} from '../utils/searchLabels';

interface Props {
  candidate: SearchCandidate;
  onPrepareSimulation: () => void;
  preparing: boolean;
  onContinueComparing: () => void;
  previewAvailable: boolean;
  onReject: () => void;
  rejecting: boolean;
  onMinimize?: () => void;
}

export function CandidateDetailPanel({
  candidate,
  onPrepareSimulation,
  preparing,
  onContinueComparing,
  previewAvailable,
  onReject,
  rejecting,
  onMinimize,
}: Props) {
  // 검증 없이 돌린 탐색은 실측 지표가 없다. 그때 개선 폭을 알 수 있는 유일한 방법이 이 후보로
  // 시뮬레이션을 실제로 돌려보는 것이므로, 수치가 없다는 이유로 준비를 막으면 안 된다.
  const measured = candidate.measuredMetrics ?? [];
  const preparedSimulation = candidate.preparedSimulation;

  return (
    <div className="search-insight-body">
      <div className="search-insight__header">
        <div className="workspace-section-heading">
          <div>
            <span>개선안 상세</span>
            <small>{operatorLabel(candidate.operatorType)}</small>
          </div>
          {onMinimize && (
            <button
              type="button"
              className="candidate-panel-close-btn"
              onClick={onMinimize}
              aria-label="개선안 상세 최소화"
            >
              −
            </button>
          )}
        </div>

        <div className="strategy-tags">
          <span>{findingLabel(candidate.originFindingType)}</span>
          <span>{candidate.round === 1 ? '1차 개선안' : `${candidate.round}차 개선안`}</span>
        </div>
        <h2>{operatorLabel(candidate.operatorType)}</h2>
        <p>{candidate.rationale?.description ?? '변경 근거가 없습니다.'}</p>
      </div>

      <div className="candidate-operations">
        <div className="candidate-operations__header">
          <strong>구조물 변경</strong>
          <span className="candidate-operations__count">
            {candidate.changeSet.ops.length}개 위치 조정
          </span>
        </div>
        <div className="candidate-operations__list">
          {candidate.changeSet.ops.map((op, index) => (
            <div className="candidate-operations__item" key={`${op.fabricId}-${index}`}>
              <span className="candidate-operations__name">구조물 #{op.fabricId}</span>
              <span className="candidate-operations__coords">
                ({formatNumber(op.before.startX)}, {formatNumber(op.before.startY)}) → (
                {formatNumber(op.after.startX)}, {formatNumber(op.after.startY)})
              </span>
            </div>
          ))}
        </div>
      </div>

      {measured.length > 0 && (
        <div className="comparison-metrics">
          <span className="comparison-metrics__title">실측 검증 지표</span>
          {measured.map((metric) => {
            const metricDelta = candidate.delta.find(
              (item) => item.metricType === metric.metricType,
            );
            return (
              <div className="comparison-metric" key={metric.metricType}>
                <div className="comparison-metric__main">
                  <span>{metricLabel(metric.metricType)}</span>
                  <strong>{formatNumber(metric.metricValue)}</strong>
                </div>
                {metricDelta && (
                  <em className="delta-badge is-improved">{formatDelta(metricDelta)}</em>
                )}
              </div>
            );
          })}
        </div>
      )}

      <div className="metric-source">
        <Info className="metric-source__icon" aria-hidden="true" />
        <span>
          {measured.length > 0
            ? '표시된 수치는 엔진이 실제로 검증한 공식 지표입니다. 준비 시 원본을 유지하고 별도의 시뮬레이션 설정(초안)이 생성됩니다.'
            : '이 개선안은 아직 시뮬레이션으로 확인하지 않았습니다. 준비 시 원본을 유지하고 별도의 시뮬레이션 설정(초안)이 생성되며, 실행하면 개선 폭을 확인할 수 있습니다.'}
        </span>
      </div>

      <div className="candidate-actions">
        {preparedSimulation ? (
          <>
            <p className="preparation-feedback is-success" role="status">
              시뮬레이션 준비됨
            </p>
            <div className="preparation-success-actions">
              <a
                className="run-simulation-button"
                href={`/simulations/${preparedSimulation.simulationId}/setup`}
              >
                시뮬레이션 설정 열기
              </a>
              <button
                type="button"
                className="continue-comparing-button"
                onClick={onContinueComparing}
              >
                계속 비교
              </button>
            </div>
          </>
        ) : (
          <>
            {!previewAvailable && (
              <p className="preparation-feedback is-error" role="alert">
                변경 배치를 확인할 수 있을 때 시뮬레이션을 준비할 수 있습니다.
              </p>
            )}
            <button
              type="button"
              className="run-simulation-button"
              disabled={preparing || !previewAvailable}
              onClick={onPrepareSimulation}
            >
              {preparing ? '시뮬레이션 준비 중...' : '이 개선안으로 시뮬레이션 준비'}
            </button>
          </>
        )}

        {candidate.changeSet.ops.length > 0 && (
          <button
            type="button"
            className="reject-candidate-button"
            disabled={rejecting}
            onClick={onReject}
            title="이 개선안에서 이동된 구조물을 고정 제약으로 추가하고 다시 탐색합니다."
          >
            <Lock className="reject-candidate-button__icon" aria-hidden="true" />
            <span>{rejecting ? '제약 반영 중...' : '해당 구조물 고정 후 재탐색'}</span>
          </button>
        )}
      </div>
    </div>
  );
}
