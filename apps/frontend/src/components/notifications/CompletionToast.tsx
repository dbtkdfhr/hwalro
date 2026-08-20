import { useEffect, type ReactNode } from 'react';
import { CheckCircle2, X } from 'lucide-react';

const TOAST_DURATION_MS = 6000;

interface CompletionToastProps {
  id: number;
  title: string;
  description: string;
  action: ReactNode;
  dismissLabel: string;
  onDismiss: (id: number) => void;
}

export function CompletionToast({
  id,
  title,
  description,
  action,
  dismissLabel,
  onDismiss,
}: CompletionToastProps) {
  useEffect(() => {
    const timer = window.setTimeout(() => onDismiss(id), TOAST_DURATION_MS);
    return () => window.clearTimeout(timer);
  }, [id, onDismiss]);

  return (
    <article
      role="status"
      className="pointer-events-auto overflow-hidden rounded-2xl border border-success/25 bg-white shadow-floating"
    >
      <div className="flex gap-3 p-4">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-success-soft text-success-strong">
          <CheckCircle2 aria-hidden="true" className="size-5" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-black text-ink">{title}</p>
          <p className="mt-1 truncate text-sm tabular-nums text-text-muted">{description}</p>
          {action}
        </div>
        <button
          type="button"
          onClick={() => onDismiss(id)}
          aria-label={dismissLabel}
          className="flex size-8 shrink-0 items-center justify-center rounded-full text-text-muted outline-none transition hover:bg-surface hover:text-ink focus-visible:ring-2 focus-visible:ring-focus-ring"
        >
          <X aria-hidden="true" className="h-4 w-4" />
        </button>
      </div>
      <div className="h-1 bg-success-strong" />
    </article>
  );
}

export function CompletionToastViewport({ children }: { children: ReactNode }) {
  return (
    <div
      className="pointer-events-none fixed top-4 right-4 z-[100] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-3"
      aria-live="polite"
      aria-label="완료 알림"
    >
      {children}
    </div>
  );
}
