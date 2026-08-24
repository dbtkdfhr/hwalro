import { X } from 'lucide-react';
import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';

export type ModalSize = 'sm' | 'md' | 'lg';

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  description?: string;
  size?: ModalSize;
  children: ReactNode;
  footer?: ReactNode;
}

const sizeClasses: Record<ModalSize, string> = {
  sm: 'max-w-md',
  md: 'max-w-xl',
  lg: 'max-w-3xl',
};

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea, input, select, [tabindex]:not([tabindex="-1"])';

let modalDepth = 0;

function Modal({ open, onClose, title, description, size = 'md', children, footer }: ModalProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const previouslyFocusedRef = useRef<HTMLElement | null>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    modalDepth += 1;
    const currentDepth = modalDepth;
    previouslyFocusedRef.current = document.activeElement as HTMLElement | null;
    panelRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (currentDepth !== modalDepth) return;
        event.stopPropagation();
        onCloseRef.current();
        return;
      }
      if (event.key === 'Tab' && panelRef.current) {
        const focusable = panelRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      modalDepth -= 1;
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previouslyFocusedRef.current?.focus();
    };
  }, [open]);

  if (!open) return null;

  return (
    <div
      role="presentation"
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 p-4 backdrop-blur-[2px]"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        className={`max-h-[calc(100dvh-2.5rem)] w-full overflow-y-auto rounded-2xl border border-line bg-surface-overlay shadow-overlay outline-none ${sizeClasses[size]}`}
      >
        <div className="flex items-start justify-between gap-4 border-b border-line-subtle px-6 py-5">
          <div className="min-w-0">
            {title ? <h2 className="text-lg font-black tracking-tight text-ink">{title}</h2> : null}
            {description ? <p className="mt-1 text-sm text-text-muted">{description}</p> : null}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-line bg-surface-raised text-text-muted shadow-neu-raised transition-[background-color,border-color,color,box-shadow] hover:border-line-strong hover:text-text-strong active:bg-surface-sunken active:shadow-neu-pressed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
          >
            <X aria-hidden="true" className="h-4 w-4" />
          </button>
        </div>
        <div className="px-6 py-5">{children}</div>
        {footer ? (
          <div className="flex items-center justify-end gap-2 border-t border-line-subtle px-6 py-4">
            {footer}
          </div>
        ) : null}
      </div>
    </div>
  );
}

export default Modal;
