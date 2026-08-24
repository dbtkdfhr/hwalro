import { AlertTriangle } from 'lucide-react';
import Button from './Button';

export interface ErrorStateProps {
  message: string;
  onRetry?: () => void;
  className?: string;
}

function ErrorState({ message, onRetry, className = '' }: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={`flex items-center justify-between gap-3 rounded-xl border border-danger/25 bg-danger-soft px-4 py-3 ${className}`}
    >
      <div className="flex min-w-0 items-center gap-2.5">
        <AlertTriangle aria-hidden="true" className="h-4 w-4 shrink-0 text-danger" />
        <p className="text-sm font-bold text-danger-strong">{message}</p>
      </div>
      {onRetry ? (
        <Button type="button" variant="secondary" size="sm" onClick={onRetry}>
          다시 시도
        </Button>
      ) : null}
    </div>
  );
}

export default ErrorState;
