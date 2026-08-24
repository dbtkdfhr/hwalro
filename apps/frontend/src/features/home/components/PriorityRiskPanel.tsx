import { ArrowRight } from 'lucide-react';
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
    borderColor: 'border-l-danger',
    bgColor: 'hover:bg-danger-soft/70',
    badgeBg: 'bg-danger/15',
    badgeText: 'text-danger',
  },
  보통: {
    label: '주의',
    borderColor: 'border-l-warning',
    bgColor: 'hover:bg-warning-soft/70',
    badgeBg: 'bg-warning/15',
    badgeText: 'text-warning-strong',
  },
  낮음: {
    label: '안내',
    borderColor: 'border-l-primary',
    bgColor: 'hover:bg-primary-soft/70',
    badgeBg: 'bg-primary/15',
    badgeText: 'text-primary',
  },
};

function RiskRow({ item }: { item: PriorityRiskItem }) {
  const config = SEVERITY_CONFIG[item.severity] ?? {
    label: item.severity,
    borderColor: 'border-l-line-strong',
    bgColor: 'home-priority-risks__row--neutral',
    badgeBg: 'bg-line',
    badgeText: 'text-text-strong',
  };

  return (
    <li>
      <Link
        to={`/risk-management?riskId=${item.id}`}
        className={`home-priority-risks__row group ${config.borderColor} ${config.bgColor}`}
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
    <section aria-label="우선 확인할 항목" className="home-priority-risks">
      <div className="home-priority-risks__header">
        <h2>우선 확인할 항목</h2>
        {!isPending && !isError && displayCount > 0 && (
          <span className="home-priority-risks__count">
            {displayCount}
          </span>
        )}
      </div>

      {isPending ? (
        <div className="home-dashboard__state">
          위험 항목을 불러오는 중입니다.
        </div>
      ) : isError ? (
        <div className="home-dashboard__state home-dashboard__state--stacked">
          <p
            role="alert"
            className="rounded-xl border border-danger/25 bg-danger-soft px-4 py-2.5 text-sm text-danger-strong"
          >
            {errorMessage}
          </p>
          <button
            type="button"
            onClick={onRetry}
            className="home-dashboard__retry"
          >
            다시 시도
          </button>
        </div>
      ) : items.length === 0 ? (
        <div className="home-dashboard__state">
          우선 확인할 항목이 없습니다.
        </div>
      ) : (
        <ul className="home-priority-risks__list">
          {items.map((item) => (
            <RiskRow key={item.id} item={item} />
          ))}
        </ul>
      )}

      <div className="home-priority-risks__footer">
        <Link
          to="/risk-management"
          className="home-priority-risks__all-link"
        >
          모든 위험 항목 확인
          <ArrowRight aria-hidden="true" className="h-4 w-4" strokeWidth={2.5} />
        </Link>
      </div>
    </section>
  );
}
