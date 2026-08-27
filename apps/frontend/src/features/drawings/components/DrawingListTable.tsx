import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/context/AuthContext';
import type { DrawingSummary } from '../types/drawing';

interface DrawingListTableProps {
  items: DrawingSummary[];
  onDelete: (drawing: DrawingSummary) => void;
  onDuplicate: (drawing: DrawingSummary) => void;
  canManage: boolean;
  /** 등록자 ID → 표시 이름. 조회 실패(403 등) 시 undefined로 이름 없이 표시한다. */
  nameById?: Map<number, string>;
}

function formatCreatedAt(value: string): string {
  const [date, time = ''] = value.split('T');
  return `${date.split('-').join('. ')}. ${time.slice(0, 5)}`;
}

function creatorLabel(
  createdBy: number,
  currentUserId: number | null,
  currentUserName: string,
  nameById: Map<number, string> | undefined,
): string {
  const name = nameById?.get(createdBy);
  if (name !== undefined && name !== '') {
    return name;
  }
  if (currentUserId !== null && createdBy === currentUserId) {
    return currentUserName;
  }
  return `#${createdBy}`;
}

function DrawingListTable({
  items,
  onDelete,
  onDuplicate,
  nameById,
  canManage,
}: DrawingListTableProps) {
  const { user } = useAuth();

  return (
    <div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[760px] table-fixed border-collapse text-left">
          <caption className="sr-only">도면 목록</caption>
          <colgroup>
            <col className="w-[28%]" />
            <col className="w-[27%]" />
            <col className="w-[12%]" />
            <col className="w-[17%]" />
            <col className="w-[16%]" />
          </colgroup>
          <thead className="bg-surface text-xs font-bold tracking-wide text-text-muted">
            <tr>
              <th className="px-6 py-4">도면명</th>
              <th className="px-4 py-4">설명</th>
              <th className="px-4 py-4">등록자</th>
              <th className="px-4 py-4">등록일</th>
              <th className="px-6 py-4 text-center">관리</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {items.map((drawing) => (
              <tr key={drawing.id} className="group transition-colors hover:bg-primary-soft/30">
                <td className="px-6 py-4">
                  <Link
                    to={`/layout/${drawing.id}`}
                    className="block rounded-md outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
                  >
                    <span className="block truncate text-sm font-bold text-ink group-hover:text-primary">
                      {drawing.title}
                    </span>
                    <span className="mt-1 block text-xs tabular-nums text-text-muted">
                      도면 #{drawing.id}
                    </span>
                  </Link>
                </td>
                <td className="px-4 py-4">
                  <span className="block truncate text-sm text-text-muted">
                    {drawing.description}
                  </span>
                </td>
                <td className="px-4 py-4 text-sm font-medium text-text-strong">
                  {creatorLabel(drawing.createdBy, user?.id ?? null, user?.name ?? '', nameById)}
                </td>
                <td className="px-4 py-4 text-sm tabular-nums text-text-strong">
                  {formatCreatedAt(drawing.createdAt)}
                </td>
                <td className="px-6 py-4 text-center">
                  <div className="flex items-center justify-center gap-1.5 opacity-0 transition-opacity duration-150 group-hover:opacity-100 focus-within:opacity-100">
                    {canManage ? (
                      <>
                        <button
                          type="button"
                          onClick={() => onDuplicate(drawing)}
                          className="h-8 min-w-[52px] whitespace-nowrap rounded-lg border border-line bg-white px-2.5 text-xs font-bold text-text-strong transition hover:border-primary hover:bg-primary-soft hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
                        >
                          복제
                        </button>
                        <button
                          type="button"
                          onClick={() => onDelete(drawing)}
                          className="h-8 min-w-[52px] whitespace-nowrap rounded-lg border border-line bg-white px-2.5 text-xs font-bold text-text-muted transition hover:border-danger/40 hover:bg-danger-soft hover:text-danger-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
                        >
                          삭제
                        </button>
                      </>
                    ) : null}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default DrawingListTable;
