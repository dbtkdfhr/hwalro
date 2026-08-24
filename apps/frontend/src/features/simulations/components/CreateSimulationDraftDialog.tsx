import { useEffect, useState } from 'react';
import { simulationApi } from '../api/simulationApi';
import type { SimulationSummary } from '../types';
import { getSimulationErrorMessage } from '../utils/getSimulationErrorMessage';
import { Button, Modal, Skeleton } from '../../../components/ui';

interface CreateSimulationDraftDialogProps {
  layoutVersionId: number;
  pending: boolean;
  onClose: () => void;
  onConfirm: (parentSimulationId?: number) => void;
}

function formatCreatedAt(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('ko-KR');
}

export function CreateSimulationDraftDialog({
  layoutVersionId,
  pending,
  onClose,
  onConfirm,
}: CreateSimulationDraftDialogProps) {
  const [summaries, setSummaries] = useState<SimulationSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedParentId, setSelectedParentId] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    setSummaries([]);
    setLoading(true);
    setError(null);
    setSelectedParentId(null);
    simulationApi
      .listByLayoutVersion(layoutVersionId)
      .then((items) => {
        if (!cancelled) setSummaries(items);
      })
      .catch((loadError: unknown) => {
        if (!cancelled) setError(getSimulationErrorMessage(loadError));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [layoutVersionId]);

  const handleClose = () => {
    if (!pending) onClose();
  };

  return (
    <Modal
      open
      onClose={handleClose}
      title="새 시뮬레이션 배치"
      description="빈 배치로 시작하거나 같은 도면 버전의 기존 인원 좌표를 복사할 수 있습니다."
      size="md"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={handleClose} disabled={pending}>
            취소
          </Button>
          <Button
            type="button"
            onClick={() => onConfirm(selectedParentId ?? undefined)}
            disabled={pending}
          >
            {pending ? 'DRAFT 생성 중...' : '배치 시작'}
          </Button>
        </>
      }
    >
      <label className="flex items-start gap-3 rounded-xl border border-line bg-surface-raised p-4 shadow-neu-raised transition-[border-color,background-color,box-shadow] has-checked:border-primary has-checked:bg-surface-sunken has-checked:shadow-neu-pressed">
        <input
          type="radio"
          name="draft-source"
          checked={selectedParentId === null}
          onChange={() => setSelectedParentId(null)}
          className="mt-1 accent-primary"
        />
        <span>
          <span className="block text-sm font-bold text-ink">빈 배치로 시작</span>
          <span className="mt-1 block text-xs text-text-muted">
            에이전트 없이 새 DRAFT를 만듭니다.
          </span>
        </span>
      </label>

      <div className="mt-3 max-h-64 space-y-2 overflow-y-auto">
        {loading ? (
          <div className="space-y-2">
            <Skeleton className="h-16 rounded-xl" />
            <Skeleton className="h-16 rounded-xl" />
          </div>
        ) : error ? (
          <p
            role="alert"
            className="rounded-xl border border-danger/25 bg-danger-soft px-4 py-3 text-sm text-danger-strong"
          >
            {error}
          </p>
        ) : summaries.length === 0 ? (
          <p className="rounded-xl border border-line bg-surface-sunken px-4 py-4 text-center text-sm text-text-muted shadow-neu-pressed">
            복사할 수 있는 이전 시뮬레이션이 없습니다.
          </p>
        ) : (
          summaries.map((summary) => (
            <label
              key={summary.id}
              className="flex items-start gap-3 rounded-xl border border-line bg-surface-raised p-4 shadow-neu-raised transition-[border-color,background-color,box-shadow] has-checked:border-primary has-checked:bg-surface-sunken has-checked:shadow-neu-pressed"
            >
              <input
                type="radio"
                name="draft-source"
                checked={selectedParentId === summary.id}
                onChange={() => setSelectedParentId(summary.id)}
                className="mt-1 accent-primary"
              />
              <span className="min-w-0 flex-1">
                <span className="flex items-center justify-between gap-3 text-sm font-bold text-ink">
                  <span className="truncate">{summary.title || `시뮬레이션 #${summary.id}`}</span>
                  <span className="shrink-0 tabular-nums text-primary">
                    {summary.totalPeople.toLocaleString()}명
                  </span>
                </span>
                <span className="mt-1 block text-xs tabular-nums text-text-muted">
                  #{summary.id} · {formatCreatedAt(summary.createdAt)} · {summary.status}
                </span>
              </span>
            </label>
          ))
        )}
      </div>
    </Modal>
  );
}
