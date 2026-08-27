import { Check, ChevronRight, CornerDownRight, Play, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';

import type { LayoutSearch, SearchCandidate } from '../api/layoutSearchApi';
import { layoutSearchReplies, trialCandidates, type SearchReply } from '../utils/searchFeed';
import {
  CANDIDATE_STATUS_LABELS,
  formatDelta,
  operatorLabel,
  SEARCH_STATUS_LABELS,
} from '../utils/searchLabels';

interface Props {
  search: LayoutSearch;
  onDeleteSimulation?: (simulationId: number) => void;
  deletingSimulationId?: number | null;
}

const CANDIDATE_STATUS_STYLES: Record<string, string> = {
  RUNNING: 'bg-primary-soft text-primary-active border border-primary/20',
  EVALUATED: 'bg-success-soft text-success-strong border border-success/20',
  NOT_IMPROVED: 'bg-soft-gray text-text-muted border border-line',
  FAILED: 'bg-danger-soft text-danger-strong border border-danger/20',
  QUEUED: 'bg-soft-gray text-text-muted border border-line',
  GENERATED: 'bg-soft-gray text-text-muted border border-line',
  REJECTED_CONSTRAINT: 'bg-danger-soft text-danger-strong border border-danger/20',
};

function DeltaCell({
  delta,
  inverse = true,
}: {
  delta: SearchCandidate['delta'][number] | undefined;
  inverse?: boolean;
}) {
  if (!delta) {
    return <span className="text-text-muted">-</span>;
  }

  // 대피 시간, 편중도 등은 수치가 감소할수록(음수) 개선됨
  const isImproved = inverse ? delta.difference < 0 : delta.difference > 0;
  const isWorsened = inverse ? delta.difference > 0 : delta.difference < 0;

  return (
    <span
      className={`font-semibold tabular-nums ${
        isImproved ? 'text-primary' : isWorsened ? 'text-danger-strong' : 'text-text-muted'
      }`}
    >
      {formatDelta(delta)}
    </span>
  );
}

function CandidateRow({
  candidate,
  onDeleteSimulation,
  deletingSimulationId,
}: {
  candidate: SearchCandidate;
  onDeleteSimulation?: (simulationId: number) => void;
  deletingSimulationId?: number | null;
}) {
  const prepared = candidate.preparedSimulation;
  const candidateStatus = candidate.status;
  const preparedStatus = prepared?.status ?? null;
  const isRunning =
    candidateStatus === 'RUNNING' || preparedStatus === 'RUNNING' || preparedStatus === 'REQUESTED';
  const isEvaluated = candidateStatus === 'EVALUATED';
  const isNotImproved = candidateStatus === 'NOT_IMPROVED';
  const isFailed = candidateStatus === 'FAILED' || preparedStatus === 'FAILED';

  const deltas = candidate.delta ?? [];
  const totalTimeDelta = deltas.find(
    (d) =>
      d.metricType === 'TOTAL_EVACUATION_TIME_SECONDS' ||
      d.metricType === 'SIMULATION_DURATION_SECONDS',
  );
  const avgTimeDelta = deltas.find((d) => d.metricType === 'AVERAGE_EVACUATION_TIME_SECONDS');
  const exitImbalanceDelta = deltas.find(
    (d) => d.metricType === 'EXIT_IMBALANCE' || d.metricType === 'EXIT_IMBALANCE_RATIO',
  );
  const maxDensityDelta = deltas.find((d) => d.metricType === 'MAX_DENSITY');

  const { statusLabel, statusBadgeStyle } = (() => {
    if (isRunning) {
      return {
        statusLabel: '검증 중',
        statusBadgeStyle: 'bg-primary-soft text-primary-active border border-primary/20',
      };
    }
    if (preparedStatus === 'COMPLETED') {
      return {
        statusLabel: isEvaluated ? '개선 확인' : '실행 완료',
        statusBadgeStyle: 'bg-success-soft text-success-strong border border-success/20',
      };
    }
    if (isEvaluated) {
      return {
        statusLabel: '개선 확인',
        statusBadgeStyle: 'bg-success-soft text-success-strong border border-success/20',
      };
    }
    if (preparedStatus === 'DRAFT') {
      return {
        statusLabel: '준비 완료',
        statusBadgeStyle: 'bg-soft-gray text-text-strong border border-line',
      };
    }
    if (isNotImproved) {
      return {
        statusLabel: '개선 미달',
        statusBadgeStyle: 'bg-soft-gray text-text-muted border border-line',
      };
    }
    if (isFailed) {
      return {
        statusLabel: '실행 실패',
        statusBadgeStyle: 'bg-danger-soft text-danger-strong border border-danger/20',
      };
    }
    if (candidateStatus === 'QUEUED') {
      return {
        statusLabel: '대기 중',
        statusBadgeStyle: 'bg-soft-gray text-text-muted border border-line',
      };
    }
    return {
      statusLabel: CANDIDATE_STATUS_LABELS[candidateStatus] ?? candidateStatus,
      statusBadgeStyle:
        CANDIDATE_STATUS_STYLES[candidateStatus] ??
        'bg-soft-gray text-text-muted border border-line',
    };
  })();

  const simulationId = prepared?.simulationId ?? null;
  const isDeleting = simulationId !== null && deletingSimulationId === simulationId;
  const simulationLinkTo = simulationId
    ? preparedStatus === 'COMPLETED'
      ? `/simulations/${simulationId}/results`
      : `/simulations/${simulationId}/setup`
    : null;

  return (
    <tr className="group/row transition-colors hover:bg-surface-elevated/70">
      {/* 1. 시뮬레이션 개선안 (31%) */}
      <td className="py-2.5 pl-6 pr-4">
        <div className="flex items-center gap-2">
          <CornerDownRight
            className="h-3.5 w-3.5 shrink-0 text-text-muted/60 transition-colors group-hover/row:text-primary"
            aria-hidden="true"
          />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-1.5">
              {simulationLinkTo ? (
                <Link
                  to={simulationLinkTo}
                  className="truncate text-sm font-bold text-text-strong transition-colors hover:text-primary group-hover/row:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
                >
                  개선안 #{candidate.candidateId}
                </Link>
              ) : (
                <span className="truncate text-sm font-bold text-text-strong">
                  개선안 #{candidate.candidateId}
                </span>
              )}
              <span className="inline-flex shrink-0 items-center rounded bg-accent-purple-soft px-1.5 py-0.5 text-[10px] font-bold text-accent-purple">
                {operatorLabel(candidate.operatorType)}
              </span>
            </div>
          </div>
        </div>
      </td>

      {/* 2. 상태 (13%) */}
      <td className="px-4 py-2.5">
        <span
          className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${statusBadgeStyle}`}
        >
          {isRunning && (
            <span
              aria-hidden="true"
              className="h-2 w-2 animate-spin rounded-full border-2 border-primary/30 border-t-primary"
            />
          )}
          {!isRunning && (isEvaluated || preparedStatus === 'COMPLETED') && (
            <Check className="h-3 w-3 text-success" strokeWidth={2.5} aria-hidden="true" />
          )}
          {statusLabel}
        </span>
      </td>

      {/* 3. 총 대피 시간 개선 (11%) */}
      <td className="px-4 py-2.5 text-xs">
        <DeltaCell delta={totalTimeDelta} inverse={true} />
      </td>

      {/* 4. 평균 대피 시간 개선 (14%) */}
      <td className="px-4 py-2.5 text-xs">
        <DeltaCell delta={avgTimeDelta} inverse={true} />
      </td>

      {/* 5. 비상구 편중도 — 비상구가 둘 이상일 때만 산출되므로 없으면 '-'로 둔다 */}
      <td className="px-4 py-2.5 text-xs">
        <DeltaCell delta={exitImbalanceDelta} inverse={true} />
      </td>

      {/* 6. 최대 밀집도 — 안전 기준을 넘긴 상승은 개선 판정에서 거부 사유가 된다 */}
      <td className="px-4 py-2.5 text-xs">
        <DeltaCell delta={maxDensityDelta} inverse={true} />
      </td>

      {/* 7. 관리 */}
      <td className="px-6 py-2.5 text-center">
        {simulationId && onDeleteSimulation ? (
          <div className="flex items-center justify-center opacity-0 transition-opacity duration-150 group-hover/row:opacity-100 focus-within:opacity-100">
            <button
              type="button"
              onClick={() => onDeleteSimulation(simulationId)}
              disabled={isDeleting || deletingSimulationId !== null}
              className="h-7 min-w-[48px] whitespace-nowrap rounded-lg border border-line bg-white px-2 text-xs font-bold text-text-muted transition hover:border-danger/40 hover:bg-danger-soft hover:text-danger-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isDeleting ? '삭제 중…' : '삭제'}
            </button>
          </div>
        ) : (
          <span className="text-xs text-text-muted">-</span>
        )}
      </td>
    </tr>
  );
}

function StatusFeedItem({ reply }: { reply: SearchReply }) {
  return (
    <div className="flex items-center gap-2 text-xs">
      {reply.tone === 'running' && (
        <span
          aria-hidden="true"
          className="h-3 w-3 animate-spin rounded-full border-2 border-primary/30 border-t-primary"
        />
      )}
      <span
        className={`font-medium ${
          reply.tone === 'failed'
            ? 'text-danger-strong'
            : reply.tone === 'running'
              ? 'text-primary-active font-semibold'
              : 'text-text-muted'
        }`}
      >
        {reply.text}
      </span>
    </div>
  );
}

export function LayoutSearchReplyThread({
  search,
  onDeleteSimulation,
  deletingSimulationId,
}: Props) {
  const candidates = trialCandidates(search);
  const replies = layoutSearchReplies(search);
  const isSearching =
    search.status === 'PENDING' ||
    search.status === 'DIAGNOSING' ||
    search.status === 'GENERATING' ||
    search.status === 'VERIFYING';

  return (
    <div className="overflow-hidden rounded-xl border border-primary/20 bg-surface shadow-xs">
      {/* 헤더 바 */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line/60 bg-primary-soft/20 px-5 py-2.5">
        <div className="flex items-center gap-2">
          <div className="flex h-5 w-5 items-center justify-center rounded bg-primary/10 text-primary shadow-xs">
            <Sparkles className="h-3 w-3" aria-hidden="true" />
          </div>
          <span className="text-xs font-bold text-ink">배치 개선안 탐색</span>
          <span
            className={`inline-flex items-center gap-1 rounded-full px-2 py-0.2 text-[11px] font-bold ${
              isSearching
                ? 'bg-primary text-white animate-pulse'
                : search.status === 'COMPLETED'
                  ? 'bg-success-soft text-success-strong'
                  : 'bg-soft-gray text-text-muted'
            }`}
          >
            {isSearching && (
              <span
                aria-hidden="true"
                className="h-2 w-2 animate-spin rounded-full border border-white/40 border-t-white"
              />
            )}
            {SEARCH_STATUS_LABELS[search.status]}
          </span>
        </div>

        <Link
          to={`/simulations/${search.baselineSimulationId}/layout-search`}
          className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-2.5 py-1 text-xs font-bold text-text-strong shadow-xs transition hover:border-primary/40 hover:bg-primary-soft hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
        >
          <Play className="h-2.5 w-2.5 fill-current" aria-hidden="true" />
          탐색 워크스페이스
          <ChevronRight className="h-3 w-3" aria-hidden="true" />
        </Link>
      </div>

      {/* 후보 목록이 있을 경우 테이블 형태로 렌더링 */}
      {candidates.length > 0 ? (
        <div className="divide-y divide-line/60">
          <table className="w-full table-fixed border-collapse text-left">
            <caption className="sr-only">발견된 배치 개선안 후보 목록</caption>
            <colgroup>
              <col className="w-[26%]" />
              <col className="w-[12%]" />
              <col className="w-[12%]" />
              <col className="w-[12%]" />
              <col className="w-[13%]" />
              <col className="w-[13%]" />
              <col className="w-[12%]" />
            </colgroup>
            <thead>
              <tr className="border-b border-line/40 bg-surface-elevated/40 text-[11px] font-semibold text-text-muted">
                <th className="py-2 pl-6 pr-4">개선안</th>
                <th className="px-4 py-2">상태</th>
                <th className="px-4 py-2">총 대피시간 개선</th>
                <th className="px-4 py-2">평균 대피시간 개선</th>
                <th className="px-4 py-2">비상구 편중도</th>
                <th className="px-4 py-2">최대 밀집도</th>
                <th className="px-6 py-2 text-center">관리</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line/30 bg-surface">
              {candidates.map((candidate) => (
                <CandidateRow
                  key={candidate.candidateId}
                  candidate={candidate}
                  onDeleteSimulation={onDeleteSimulation}
                  deletingSimulationId={deletingSimulationId}
                />
              ))}
            </tbody>
          </table>

          {/* 탐색 진행 중일 때만 하단 피드백 간략 표시 */}
          {isSearching && (
            <div className="flex flex-wrap items-center gap-4 bg-surface/80 px-6 py-2">
              {replies
                .filter((r) => r.tone === 'running')
                .map((reply) => (
                  <StatusFeedItem key={reply.key} reply={reply} />
                ))}
            </div>
          )}
        </div>
      ) : (
        /* 탐색 중이거나 후보가 아직 없을 때 */
        <div className="flex flex-col gap-2 px-6 py-3">
          <div className="space-y-1">
            {replies
              .filter((r) => isSearching || r.tone !== 'done')
              .map((reply) => (
                <StatusFeedItem key={reply.key} reply={reply} />
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
