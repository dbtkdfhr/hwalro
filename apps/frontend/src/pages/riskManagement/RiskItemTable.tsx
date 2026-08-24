import { ShieldAlert } from 'lucide-react';
import { Badge, EmptyState } from '../../components/ui';
import type { BadgeTone } from '../../components/ui';
import type { Risk } from '../../features/risks/types/risks';
import { formatDate } from '../../features/risks/utils/formatDate';

const TABLE_HEADERS = ['위험 예상 항목', '시뮬레이션', '심각도', '담당자', '등록일'] as const;

const TABLE_COLUMNS = 'grid-cols-[minmax(0,2.5fr)_minmax(0,1.5fr)_1fr_1fr_2fr]';

const SEVERITY_TONES: Record<string, BadgeTone> = {
  높음: 'danger',
  보통: 'neutral',
  낮음: 'primary',
};

function RiskItemTable({
  items,
  selectedId,
  onSelect,
  emptyTitle = '등록된 위험 항목이 없습니다.',
  emptyDescription,
}: {
  items: Risk[];
  selectedId: number | null;
  onSelect: (id: number) => void;
  emptyTitle?: string;
  emptyDescription?: string;
}) {
  if (items.length === 0) {
    return (
      <div className="p-5 sm:p-7">
        <EmptyState icon={ShieldAlert} title={emptyTitle} description={emptyDescription} />
      </div>
    );
  }

  return (
    <div>
      <div
        className={`grid ${TABLE_COLUMNS} items-center gap-x-6 bg-surface-sunken px-5 py-4 sm:px-7`}
      >
        {TABLE_HEADERS.map((header) => (
          <span key={header} className="text-xs font-bold tracking-wide text-text-muted">
            {header}
          </span>
        ))}
      </div>

      <ul className="divide-y divide-line">
        {items.map((item) => {
          const isSelected = item.id === selectedId;
          return (
            <li key={item.id}>
              <button
                type="button"
                onClick={() => onSelect(item.id)}
                aria-pressed={isSelected}
                className={`grid w-full ${TABLE_COLUMNS} items-center gap-x-6 px-5 py-4 text-left outline-none transition-colors focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus-ring sm:px-7 ${
                  isSelected
                    ? 'bg-surface-sunken shadow-neu-pressed'
                    : 'bg-transparent hover:bg-primary-soft/35'
                }`}
              >
                <div className="flex min-w-0 items-center gap-2">
                  <span className="truncate text-sm font-bold text-ink">{item.title}</span>
                  {item.attachedLaws.length > 0 && (
                    <Badge tone="primary" className="shrink-0">
                      법령 {item.attachedLaws.length}건
                    </Badge>
                  )}
                </div>
                <span className="truncate text-sm text-text-muted">
                  {item.simulationTitle ?? ''}
                </span>
                <Badge tone={SEVERITY_TONES[item.severity] ?? 'neutral'}>{item.severity}</Badge>
                <span className="text-sm tabular-nums text-text-strong">
                  {item.assigneeName ?? '-'}
                </span>
                <span className="text-sm tabular-nums text-text-strong">
                  {formatDate(item.createdAt)}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

export default RiskItemTable;
