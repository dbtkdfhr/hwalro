import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  Clock,
  Compass,
  Info,
  Layers,
  Sparkles,
  TrendingDown,
} from 'lucide-react';
import type { SearchCandidate } from '../api/layoutSearchApi';
import {
  candidateResultBadgeStyle,
  candidateResultLabel,
  findingLabel,
  formatDelta,
  formatNumber,
  metricLabel,
  operatorLabel,
  RECOMMENDATION_LABELS,
  RECOMMENDATION_TYPE_META,
  rejectReasonLabel,
} from '../utils/searchLabels';

interface Props {
  candidate: SearchCandidate;
  onPrepareSimulation: () => void;
  preparing: boolean;
  previewAvailable: boolean;
  onMinimize?: () => void;
}

export function CandidateDetailPanel({
  candidate,
  onPrepareSimulation,
  preparing,
  previewAvailable,
  onMinimize,
}: Props) {
  const measured = (candidate.measuredMetrics ?? []).filter((metric) =>
    ['TOTAL_EVACUATION_TIME_SECONDS', 'AVERAGE_EVACUATION_TIME_SECONDS'].includes(
      metric.metricType,
    ),
  );
  const preparedSimulation = candidate.preparedSimulation;
  const isFailed = candidate.status === 'FAILED';
  const failureReason = rejectReasonLabel(candidate.rejectReason);

  return (
    <div className="flex flex-col gap-4 p-5 text-ink">
      {/* 1. 헤더 및 태그 배지 영역 */}
      <div className="flex flex-col gap-2.5 border-b border-line/60 pb-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-xs font-black uppercase tracking-wider text-primary">
              개선안 #{candidate.candidateId}
            </span>
            <span className="rounded bg-primary-soft/60 px-1.5 py-0.5 text-[10px] font-bold text-primary-active">
              Round {candidate.round}
            </span>
          </div>
          {onMinimize && (
            <button
              type="button"
              className="flex h-6 w-6 items-center justify-center rounded-md border border-line bg-surface text-sm font-medium text-text-muted transition hover:border-line-strong hover:bg-surface-subtle hover:text-text-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              onClick={onMinimize}
              aria-label="개선안 상세 최소화"
            >
              −
            </button>
          )}
        </div>

        {/* 전략 및 상태 태그 그룹 */}
        <div className="flex flex-wrap items-center gap-1.5">
          {/* 상태 배지 */}
          <span
            className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-bold ${candidateResultBadgeStyle(
              candidate,
            )}`}
          >
            {candidateResultLabel(candidate)}
          </span>

          {/* 추천 유형 배지 */}
          {candidate.recommendationTypes && candidate.recommendationTypes.length > 0 ? (
            candidate.recommendationTypes.map((type) => {
              const meta = RECOMMENDATION_TYPE_META[type];
              return (
                <span
                  key={type}
                  className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-bold ${
                    meta?.badgeStyle ?? 'bg-soft-gray text-text-muted border border-line'
                  }`}
                >
                  {meta?.label ?? RECOMMENDATION_LABELS[type] ?? type}
                </span>
              );
            })
          ) : (
            <span className="inline-flex items-center rounded-md border border-line bg-soft-gray px-2 py-0.5 text-xs font-bold text-text-muted">
              비교 후보
            </span>
          )}

          {/* 원인 진단 배지 */}
          <span className="inline-flex items-center gap-1 rounded-md border border-sky-200/80 bg-sky-50 px-2 py-0.5 text-xs font-bold text-sky-800">
            <Compass className="h-3 w-3" aria-hidden="true" />
            {findingLabel(candidate.originFindingType)} 진단
          </span>
        </div>

        {/* 개선 오퍼레이터 타이틀 */}
        <div>
          <h2 className="flex items-center gap-1.5 text-base font-black text-ink">
            <Sparkles className="h-4 w-4 text-primary" aria-hidden="true" />
            {operatorLabel(candidate.operatorType)}
          </h2>
          {candidate.rationale?.description && (
            <p className="mt-1.5 rounded-lg border border-line/40 bg-surface-subtle p-2.5 text-xs leading-relaxed text-text-strong">
              {candidate.rationale.description}
            </p>
          )}
        </div>
      </div>

      {/* 2. 지표 비교 영역 (실측 검증 지표 or 가측 기하학적 예상안) */}
      <div className="flex flex-col gap-2.5">
        <div className="flex items-center justify-between">
          <span className="text-xs font-black tracking-wide text-text-muted">
            {measured.length > 0 ? '실측 검증 지표' : '기하학적 개선 예상'}
          </span>
          {measured.length > 0 && (
            <span className="text-[11px] font-semibold text-text-faint">기준 대비 변화율</span>
          )}
        </div>

        {measured.length > 0 ? (
          <div className="flex flex-col gap-2">
            {measured.map((metric) => {
              const metricDelta = candidate.delta.find(
                (item) => item.metricType === metric.metricType,
              );
              const isImproved = metricDelta ? metricDelta.difference < 0 : false;
              const isWorse = metricDelta ? metricDelta.difference > 0 : false;
              const isTotal = metric.metricType === 'TOTAL_EVACUATION_TIME_SECONDS';

              return (
                <div
                  key={metric.metricType}
                  className="flex items-center justify-between rounded-xl border border-line/60 bg-surface px-3.5 py-2.5 shadow-xs"
                >
                  <div className="flex flex-col gap-0.5">
                    <span className="flex items-center gap-1.5 text-xs font-bold text-text-muted">
                      {isTotal ? (
                        <Clock className="h-3.5 w-3.5 text-primary" aria-hidden="true" />
                      ) : (
                        <TrendingDown className="h-3.5 w-3.5 text-primary" aria-hidden="true" />
                      )}
                      {metricLabel(metric.metricType)}
                    </span>
                    {metricDelta && (
                      <span className="text-[11px] text-text-faint tabular-nums">
                        기준 {formatNumber(metricDelta.baseline)}초
                      </span>
                    )}
                  </div>

                  <div className="flex flex-col items-end gap-1">
                    <strong className="text-base font-black tracking-tight text-ink tabular-nums">
                      {formatNumber(metric.metricValue)}
                      <span className="ml-0.5 text-xs font-semibold text-text-muted">초</span>
                    </strong>
                    {metricDelta && (
                      <span
                        className={`inline-flex shrink-0 items-center rounded-md px-1.5 py-0.5 text-[11px] font-black tabular-nums whitespace-nowrap ${
                          isImproved
                            ? 'bg-success-soft text-success-strong border border-success/30'
                            : isWorse
                              ? 'bg-danger-soft text-danger-strong border border-danger/30'
                              : 'bg-soft-gray text-text-muted border border-line'
                        }`}
                      >
                        {formatDelta(metricDelta)}
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="rounded-xl border border-primary/20 bg-primary-soft/20 p-3.5">
            <div className="flex items-center gap-2">
              <Compass className="h-4 w-4 text-primary" aria-hidden="true" />
              <strong className="text-xs font-bold text-ink">실측 전 기하학적 최적화</strong>
            </div>
            <p className="mt-1.5 text-xs leading-relaxed text-text-muted">
              공간 기하 구조와 대피 경로를 분석하여 충돌 및 병목을 최소화한 배치안입니다. 아래
              버튼으로 시뮬레이션을 생성하여 정확한 대피 시간 지표를 검증할 수 있습니다.
            </p>
          </div>
        )}

        <div className="flex items-start gap-1.5 rounded-lg bg-surface-subtle p-2 text-[11px] text-text-muted">
          <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-text-faint" aria-hidden="true" />
          <span>
            {measured.length > 0
              ? '엔진이 직접 시뮬레이션하여 검증한 공식 지표입니다.'
              : '원본 도면을 보존한 채 별도의 독립 시뮬레이션 설정으로 생성됩니다.'}
          </span>
        </div>
      </div>

      {/* 3. 구조물 변경 내역 */}
      <div className="flex flex-col gap-2 rounded-xl border border-line/60 bg-surface p-3.5 shadow-xs">
        <div className="flex items-center justify-between border-b border-line/40 pb-2">
          <div className="flex items-center gap-1.5">
            <Layers className="h-3.5 w-3.5 text-primary" aria-hidden="true" />
            <strong className="text-xs font-bold text-ink">구조물 배치 변경</strong>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="rounded bg-primary-soft px-1.5 py-0.5 text-[10px] font-bold text-primary">
              {candidate.changeSet.ops.length}개 위치 조정
            </span>
            {candidate.totalMoveDistance !== null && candidate.totalMoveDistance > 0 && (
              <span className="rounded bg-soft-gray px-1.5 py-0.5 text-[10px] font-bold text-text-muted">
                총 {formatNumber(candidate.totalMoveDistance)}m 이동
              </span>
            )}
          </div>
        </div>

        <div className="max-h-40 divide-y divide-line/30 overflow-y-auto pr-1">
          {candidate.changeSet.ops.map((op, index) => (
            <div
              className="flex items-center justify-between py-1.5 text-xs"
              key={`${op.fabricId}-${index}`}
            >
              <span className="font-semibold text-text-strong">구조물 #{op.fabricId}</span>
              <div className="flex items-center gap-1 font-mono text-[11px] text-text-muted">
                <span>
                  ({formatNumber(op.before.startX)}, {formatNumber(op.before.startY)})
                </span>
                <ArrowRight className="h-3 w-3 text-text-faint" aria-hidden="true" />
                <span className="font-bold text-primary">
                  ({formatNumber(op.after.startX)}, {formatNumber(op.after.startY)})
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 4. 하단 액션 버튼 및 피드백 */}
      <div className="mt-1 flex flex-col gap-2">
        {preparedSimulation ? (
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-1.5 rounded-lg border border-success/30 bg-success-soft px-3 py-2 text-xs font-bold text-success-strong">
              <CheckCircle2 className="h-4 w-4 text-success" aria-hidden="true" />
              <span>실측 시뮬레이션 저장됨</span>
            </div>
            <Link
              className="inline-flex h-10 w-full items-center justify-center rounded-xl bg-primary px-4 text-xs font-black text-white shadow-xs transition hover:bg-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              to={
                preparedSimulation.status === 'COMPLETED'
                  ? `/simulations/${preparedSimulation.simulationId}/results`
                  : `/simulations/${preparedSimulation.simulationId}/setup`
              }
            >
              {preparedSimulation.status === 'COMPLETED'
                ? '시뮬레이션 결과 열기'
                : '시뮬레이션 설정 열기'}
            </Link>
          </div>
        ) : candidate.status === 'NOT_IMPROVED' || isFailed ? (
          <div className="flex items-start gap-2 rounded-xl border border-danger/30 bg-danger-soft p-3 text-xs text-danger-strong">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <div>
              <strong className="block font-bold">
                {isFailed ? '검증 실행 실패' : '개선 기준 미달'}
              </strong>
              <p className="mt-0.5 text-[11px] leading-relaxed">
                {isFailed
                  ? (failureReason ??
                    '검증 실행 중 오류가 발생하여 시뮬레이션을 생성할 수 없습니다.')
                  : '대피 시간 단축 효과가 기준에 미치지 못해 시뮬레이션이 자동 저장되지 않았습니다.'}
              </p>
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-1.5">
            {!previewAvailable && (
              <p className="text-center text-xs font-medium text-danger-strong" role="alert">
                변경 배치를 확인할 수 있을 때 시뮬레이션을 준비할 수 있습니다.
              </p>
            )}
            <button
              type="button"
              className="inline-flex h-10 w-full items-center justify-center gap-1.5 rounded-xl bg-primary px-4 text-xs font-black text-white shadow-xs transition hover:bg-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-50"
              disabled={preparing || !previewAvailable}
              onClick={onPrepareSimulation}
            >
              {preparing ? (
                <>
                  <span
                    aria-hidden="true"
                    className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white/40 border-t-white"
                  />
                  <span>시뮬레이션 준비 중…</span>
                </>
              ) : (
                <>
                  <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
                  <span>이 개선안으로 시뮬레이션 진행</span>
                </>
              )}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
