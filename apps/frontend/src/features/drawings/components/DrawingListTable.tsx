import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/context/AuthContext';
import type { DrawingSummary } from '../types/drawing';
import DrawingRowActions from './DrawingRowActions';

interface DrawingListTableProps {
  items: DrawingSummary[];
  onDelete: (drawing: DrawingSummary) => void;
  onDuplicate: (drawing: DrawingSummary) => void;
}

function formatCreatedAt(value: string): string {
  const [date, time = ''] = value.split('T');
  return `${date.split('-').join('. ')}. ${time.slice(0, 5)}`;
}

function creatorLabel(
  createdBy: number,
  currentUserId: number | null,
  currentUserName: string,
): string {
  if (currentUserId !== null && createdBy === currentUserId) {
    return currentUserName;
  }
  return `#${createdBy}`;
}

function DrawingListTable({ items, onDelete, onDuplicate }: DrawingListTableProps) {
  const { user } = useAuth();
  const [openMenuId, setOpenMenuId] = useState<number | null>(null);

  return (
    <div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[760px] table-fixed border-collapse text-left">
          <caption className="sr-only">도면 목록</caption>
          <colgroup>
            <col className="w-[30%]" />
            <col className="w-[28%]" />
            <col className="w-[12%]" />
            <col className="w-[17%]" />
            <col className="w-[13%]" />
          </colgroup>
          <thead className="bg-surface text-xs font-bold tracking-wide text-text-muted">
            <tr>
              <th className="px-6 py-4">도면명</th>
              <th className="px-4 py-4">설명</th>
              <th className="px-4 py-4">등록자</th>
              <th className="px-4 py-4">등록일</th>
              <th className="px-6 py-4 text-right">관리</th>
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
                  {creatorLabel(drawing.createdBy, user?.id ?? null, user?.name ?? '')}
                </td>
                <td className="px-4 py-4 text-sm tabular-nums text-text-strong">
                  {formatCreatedAt(drawing.createdAt)}
                </td>
                <td className="px-6 py-4 text-right">
                  <DrawingRowActions
                    label={`도면 #${drawing.id} 관리 메뉴`}
                    open={openMenuId === drawing.id}
                    onToggle={() => setOpenMenuId(openMenuId === drawing.id ? null : drawing.id)}
                    onDuplicate={() => {
                      setOpenMenuId(null);
                      onDuplicate(drawing);
                    }}
                    onDelete={() => {
                      setOpenMenuId(null);
                      onDelete(drawing);
                    }}
                  />
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
