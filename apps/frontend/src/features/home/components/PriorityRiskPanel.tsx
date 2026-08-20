import { Link } from 'react-router-dom';
import type { PriorityRiskItem } from '../types/home';

interface PriorityRiskPanelProps {
  items: PriorityRiskItem[];
  totalCount?: number;
  isPending: boolean;
  isError: boolean;
  errorMessage: string;
  onRetry: () => void;
}

interface SeverityStyle {
  label: string;
  borderColor: string;
  bgColor: string;
  badgeBg: string;
  badgeText: string;
}

const SEVERITY_CONFIG: Record<string, SeverityStyle> = {
  높음: {
    label: '긴급',
    borderColor: 'border-danger',
    bgColor: 'bg-danger-soft/70 hover:bg-danger-soft',
    badgeBg: 'bg-danger/15',
    badgeText: 'text-danger',
  },
  보통: {
    label: '주의',
    borderColor: 'border-warning',
    bgColor: 'bg-warning-soft/70 hover:bg-warning-soft',
    badgeBg: 'bg-warning/15',
    badgeText: 'text-warning-strong',
  },
  낮음: {
    label: '안내',
    borderColor: 'border-primary',
    bgColor: 'bg-primary-soft/70 hover:bg-primary-soft',
    badgeBg: 'bg-primary/15',
    badgeText: 'text-primary',
  },
};

function RiskRow({ item }: { item: PriorityRiskItem }) {
  const config = SEVERITY_CONFIG[item.severity] ?? {
    label: item.severity,
    borderColor: 'border-line',
    bgColor: 'bg-surface hover:bg-surface/80',
    badgeBg: 'bg-line',
    badgeText: 'text-text-strong',
  };

  return (
    <li>
      <Link
        to={`/risk-management?riskId=${item.id}`}
        className={`group block rounded-xl border-l-4 ${config.borderColor} ${config.bgColor} px-4 py-3 transition-colors hover:shadow-xs`}
      >
        <div className="flex items-center justify-between gap-2">
          <span
            className={`inline-flex items-center rounded-md px-1.5 py-0.5 text-xs font-bold ${config.badgeBg} ${config.badgeText}`}
          >
            {config.label}
          </span>
          <span className="text-xs text-text-muted">
            {item.status}
            {item.assigneeName ? ` · 담당 ${item.assigneeName}` : ''}
          </span>
        </div>
        <p className="mt-2 truncate text-sm font-bold text-ink group-hover:text-primary">
          {item.title}
        </p>
      </Link>
    </li>
  );
}

export function PriorityRiskPanel({
  items,
  totalCount,
  isPending,
  isError,
  errorMessage,
  onRetry,
}: PriorityRiskPanelProps) {
  const displayCount = totalCount ?? items.length;

  return (
    <section
      aria-label="우선 확인할 항목"
      className="flex h-full min-h-80 flex-col rounded-2xl border border-line bg-white p-6 shadow-sm shadow-ink/5"
    >
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-black tracking-tight text-ink">우선 확인할 항목</h2>
        {!isPending && !isError && displayCount > 0 && (
          <span className="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-danger-soft px-2.5 text-xs font-bold tabular-nums text-danger">
            {displayCount}
          </span>
        )}
      </div>

      {isPending ? (
        <div className="flex flex-1 items-center justify-center text-sm text-text-muted">
          위험 항목을 불러오는 중입니다.
        </div>
      ) : isError ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
          <p
            role="alert"
            className="rounded-xl border border-danger/25 bg-danger-soft px-4 py-2.5 text-sm text-danger-strong"
          >
            {errorMessage}
          </p>
          <button
            type="button"
            onClick={onRetry}
            className="rounded-lg border border-line bg-white px-4 py-2 text-sm font-bold text-text-strong hover:bg-surface"
          >
            다시 시도
          </button>
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-1 items-center justify-center text-center text-sm text-text-muted">
          우선 확인할 항목이 없습니다.
        </div>
      ) : (
        <ul className="mt-4 flex flex-col gap-2.5">
          {items.map((item) => (
            <RiskRow key={item.id} item={item} />
          ))}
        </ul>
      )}

      <div className="mt-auto pt-4">
        <Link
          to="/risk-management"
          className="flex items-center justify-between gap-3 rounded-xl bg-ink px-5 py-3.5 text-sm font-bold text-white transition-opacity hover:opacity-90"
        >
          모든 위험 항목 확인
          <span
            aria-hidden="true"
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-lime text-ink"
          >
            <svg
              className="h-3.5 w-3.5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
          </span>
        </Link>
      </div>
    </section>
  );
}
