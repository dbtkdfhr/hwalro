import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

export interface EmptyStateProps {
  icon?: LucideIcon;
  title: string;
  description?: string;
  action?: ReactNode;
}

function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-line bg-surface-sunken px-6 py-14 text-center shadow-neu-pressed">
      {Icon ? (
        <div className="flex h-12 w-12 items-center justify-center rounded-full border border-line bg-surface-raised text-primary shadow-neu-raised">
          <Icon aria-hidden="true" className="h-6 w-6" />
        </div>
      ) : null}
      <p className="mt-4 text-sm font-bold text-text-strong">{title}</p>
      {description ? (
        <p className="mt-1 max-w-sm text-sm leading-relaxed text-text-muted [text-wrap:pretty]">
          {description}
        </p>
      ) : null}
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}

export default EmptyState;
