import { Link } from 'react-router-dom';
import { STATUS_LABELS, STATUS_STYLES } from '../../simulations/constants/simulationStatus';
import type { RecentSimulationRow } from '../types/home';

function formatDateTime(value: string | null): string {
  if (!value) return '-';
  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

interface RecentSimulationTableProps {
  rows: RecentSimulationRow[];
  isPending: boolean;
  isError: boolean;
  errorMessage: string;
  onRetry: () => void;
}

function CardShell({ children }: { children: React.ReactNode }) {
  return (
    <section
      aria-label="최근 시뮬레이션"
      className="flex min-h-80 flex-col rounded-2xl border border-line bg-white shadow-sm shadow-ink/5"
    >
      {children}
    </section>
  );
}

export function RecentSimulationTable({
  rows,
  isPending,
  isError,
  errorMessage,
  onRetry,
}: RecentSimulationTableProps) {
  return (
    <CardShell>
      <div className="flex items-center justify-between px-6 py-5">
        <h2 className="text-lg font-black tracking-tight text-ink">최근 시뮬레이션</h2>
        <Link to="/simulations" className="text-xs font-bold text-text-muted hover:text-primary">
          전체보기 ›
        </Link>
      </div>

      {isPending ? (
        <div className="flex flex-1 items-center justify-center text-sm text-text-muted">
          시뮬레이션을 불러오는 중입니다.
        </div>
      ) : isError ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-4 px-6 text-center">
          <p
            role="alert"
            className="rounded-xl border border-danger/25 bg-danger-soft px-5 py-3 text-sm text-danger-strong"
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
      ) : rows.length === 0 ? (
        <div className="flex flex-1 items-center justify-center px-6 text-center text-sm text-text-muted">
          생성된 시뮬레이션이 없습니다.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px] border-collapse text-left">
            <caption className="sr-only">최근 실행한 시뮬레이션 목록</caption>
            <thead className="bg-surface text-xs font-bold tracking-wide text-text-muted">
              <tr>
                <th className="px-6 py-3">시뮬레이션</th>
                <th className="px-4 py-3">실행 일시</th>
                <th className="px-4 py-3">담당자</th>
                <th className="px-4 py-3">상태</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((row) => (
                <tr key={row.id} className="transition-colors hover:bg-primary-soft/30">
                  <td className="px-6 py-4">
                    {row.path ? (
                      <Link
                        to={row.path}
                        className="block max-w-56 truncate rounded text-sm font-bold text-ink outline-none hover:text-primary focus-visible:ring-2 focus-visible:ring-primary"
                      >
                        {row.title || row.layoutTitle}
                      </Link>
                    ) : (
                      <span
                        aria-disabled="true"
                        className="block max-w-56 cursor-not-allowed truncate text-sm font-bold text-text-muted"
                      >
                        {row.title || row.layoutTitle}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-4 text-sm tabular-nums text-text-muted">
                    {formatDateTime(row.executedAt)}
                  </td>
                  <td className="px-4 py-4 text-sm text-text-strong">
                    {row.assigneeName ?? `사용자 #${row.createdBy}`}
                  </td>
                  <td className="px-4 py-4">
                    <span
                      className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ${STATUS_STYLES[row.status]}`}
                    >
                      {STATUS_LABELS[row.status]}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </CardShell>
  );
}
