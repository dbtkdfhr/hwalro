import type { SimulationWorkSummary } from '../types/home';

interface WorkStatusCardsProps {
  summary: SimulationWorkSummary;
  isPending: boolean;
  isError: boolean;
  errorMessage: string;
  onRetry: () => void;
}

function StatusCard({
  label,
  count,
  dotClassName,
}: {
  label: string;
  count: number;
  dotClassName: string;
}) {
  return (
    <div className="min-w-56 flex-1 rounded-2xl border border-line bg-white p-5 shadow-sm shadow-ink/5 sm:max-w-72">
      <div className="flex items-center gap-2.5">
        <span aria-hidden="true" className={`h-3 w-3 rounded-full ${dotClassName}`} />
        <p className="text-sm font-bold text-text-strong">{label}</p>
      </div>
      <p className="mt-4 text-3xl font-black tabular-nums text-ink">
        {count.toLocaleString()}
        <span className="ml-1.5 text-sm font-medium text-text-muted">건</span>
      </p>
    </div>
  );
}

export function WorkStatusCards({
  summary,
  isPending,
  isError,
  errorMessage,
  onRetry,
}: WorkStatusCardsProps) {
  if (isPending) {
    return (
      <section aria-label="내 업무 현황" className="flex min-h-28 items-center">
        <p className="text-sm text-text-muted">업무 현황을 불러오는 중입니다.</p>
      </section>
    );
  }

  if (isError) {
    return (
      <section aria-label="내 업무 현황" className="flex flex-wrap items-center gap-3">
        <p
          role="alert"
          className="rounded-xl border border-danger/25 bg-danger-soft px-4 py-2.5 text-sm text-danger-strong"
        >
          {errorMessage}
        </p>
        <button
          type="button"
          onClick={onRetry}
          className="rounded-lg border border-line bg-white px-3 py-2 text-sm font-bold text-text-strong hover:bg-surface"
        >
          다시 시도
        </button>
      </section>
    );
  }

  return (
    <section aria-label="내 업무 현황" className="flex flex-wrap gap-4">
      <StatusCard
        label="시뮬레이션 처리 중"
        count={summary.inProgressCount}
        dotClassName="bg-info"
      />
      <StatusCard
        label="이번 주 완료"
        count={summary.completedThisWeekCount}
        dotClassName="bg-primary"
      />
    </section>
  );
}
