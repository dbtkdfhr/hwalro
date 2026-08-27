import type { SimulationWorkSummary } from '../types/home';

interface WorkStatusCardsProps {
  summary: SimulationWorkSummary;
  isPending: boolean;
  isError: boolean;
  errorMessage: string;
  onRetry: () => void;
}

function StatusCard({ label, count }: { label: string; count: number }) {
  return (
    <div className="home-work-status__item">
      <div className="home-work-status__label">
        <p>{label}</p>
      </div>
      <p className="home-work-status__value">
        {count.toLocaleString()}
        <span>건</span>
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
      <section
        aria-label="내 업무 현황"
        aria-busy="true"
        className="home-work-status__list home-work-status__skeleton"
      >
        <span className="sr-only">업무 현황을 불러오는 중입니다.</span>
        {[0, 1].map((item) => (
          <div key={item} aria-hidden="true" className="home-work-status__item">
            <span className="home-skeleton home-skeleton--label" />
            <span className="home-skeleton home-skeleton--metric" />
          </div>
        ))}
      </section>
    );
  }

  if (isError) {
    return (
      <section aria-label="내 업무 현황" className="home-work-status__state">
        <p
          role="alert"
          className="rounded-xl border border-danger/25 bg-danger-soft px-4 py-2.5 text-sm text-danger-strong"
        >
          {errorMessage}
        </p>
        <button type="button" onClick={onRetry} className="home-dashboard__retry">
          다시 시도
        </button>
      </section>
    );
  }

  return (
    <section aria-label="내 업무 현황" className="home-work-status__list">
      <StatusCard label="시뮬레이션 처리 중" count={summary.inProgressCount} />
      <StatusCard label="이번 주 완료" count={summary.completedThisWeekCount} />
    </section>
  );
}
