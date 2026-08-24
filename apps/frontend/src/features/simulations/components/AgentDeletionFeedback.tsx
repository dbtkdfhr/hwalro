import { useEffect, useRef } from 'react';
import { Check, TriangleAlert, X } from 'lucide-react';
import { Button } from '../../../components/ui';

interface AgentDeletionConfirmDialogProps {
  count: number;
  onCancel: () => void;
  onConfirm: () => void;
}

interface AgentDeletionSuccessToastProps {
  count: number;
  onClose: () => void;
  className?: string;
}

export function AgentDeletionConfirmDialog({
  count,
  onCancel,
  onConfirm,
}: AgentDeletionConfirmDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    dialog.showModal();
    return () => {
      if (dialog.open) dialog.close();
    };
  }, []);

  return (
    <dialog
      ref={dialogRef}
      role="alertdialog"
      aria-labelledby="agent-deletion-confirm-title"
      aria-describedby="agent-deletion-confirm-description"
      onCancel={(event) => {
        event.preventDefault();
        onCancel();
      }}
      className="m-0 h-dvh max-h-none w-screen max-w-none border-0 bg-transparent p-4 open:flex open:items-start open:justify-end backdrop:bg-ink/15"
    >
      <article className="w-80 overflow-hidden rounded-2xl border border-danger/25 bg-surface-overlay shadow-neu-floating">
        <div className="flex gap-3 p-4">
          <div
            className="flex size-9 shrink-0 items-center justify-center rounded-full bg-danger-soft text-danger"
            aria-hidden="true"
          >
            <TriangleAlert className="h-5 w-5" />
          </div>
          <div className="min-w-0 flex-1">
            <h2
              id="agent-deletion-confirm-title"
              className="text-sm font-black tabular-nums text-ink"
            >
              에이전트 {count.toLocaleString()}명을 삭제할까요?
            </h2>
            <p
              id="agent-deletion-confirm-description"
              className="mt-1 text-xs leading-5 text-text-muted"
            >
              위험구역과 시뮬레이션 조건은 유지됩니다.
            </p>
            <div className="mt-3 flex justify-end gap-2">
              <Button type="button" variant="secondary" size="sm" onClick={onCancel} autoFocus>
                취소
              </Button>
              <Button type="button" variant="danger" size="sm" onClick={onConfirm}>
                전체 삭제
              </Button>
            </div>
          </div>
          <button
            type="button"
            onClick={onCancel}
            aria-label="에이전트 전체 삭제 취소"
            className="flex size-7 shrink-0 items-center justify-center rounded-full border border-transparent text-text-muted outline-none transition-[background-color,border-color,color,box-shadow] hover:border-line hover:bg-surface-raised hover:text-ink hover:shadow-neu-raised active:bg-surface-sunken active:shadow-neu-pressed focus-visible:ring-2 focus-visible:ring-focus-ring"
          >
            <X aria-hidden="true" className="h-4 w-4" />
          </button>
        </div>
        <div className="h-1 bg-danger" />
      </article>
    </dialog>
  );
}

export function AgentDeletionSuccessToast({
  count,
  onClose,
  className,
}: AgentDeletionSuccessToastProps) {
  return (
    <article
      role="status"
      aria-live="polite"
      className={`absolute right-4 top-4 z-20 w-80 overflow-hidden rounded-2xl border border-success/25 bg-surface-overlay shadow-neu-floating ${className ?? ''}`}
    >
      <div className="flex gap-3 p-4">
        <div
          className="flex size-9 shrink-0 items-center justify-center rounded-full bg-success-soft text-success-strong"
          aria-hidden="true"
        >
          <Check className="h-5 w-5" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-black tabular-nums text-ink">
            에이전트 {count.toLocaleString()}명을 삭제했습니다.
          </p>
          <p className="mt-1 text-xs leading-5 text-text-muted">
            위험구역과 시뮬레이션 조건은 유지됩니다.
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="에이전트 삭제 알림 닫기"
          className="flex size-7 shrink-0 items-center justify-center rounded-full border border-transparent text-text-muted outline-none transition-[background-color,border-color,color,box-shadow] hover:border-line hover:bg-surface-raised hover:text-ink hover:shadow-neu-raised active:bg-surface-sunken active:shadow-neu-pressed focus-visible:ring-2 focus-visible:ring-focus-ring"
        >
          <X aria-hidden="true" className="h-4 w-4" />
        </button>
      </div>
      <div className="h-1 bg-success-strong" />
    </article>
  );
}
