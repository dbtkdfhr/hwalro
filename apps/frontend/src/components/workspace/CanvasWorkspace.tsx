import { forwardRef, type ButtonHTMLAttributes, type HTMLAttributes, type ReactNode } from 'react';
import { ArrowLeft } from 'lucide-react';
import { Button } from '../ui';
import './canvasWorkspace.css';

function joinClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(' ');
}

interface CanvasWorkspaceProps extends HTMLAttributes<HTMLElement> {
  children: ReactNode;
}

export function CanvasWorkspace({ children, className, ...rest }: CanvasWorkspaceProps) {
  return (
    <main className={joinClassNames('canvas-workspace', className)} {...rest}>
      {children}
    </main>
  );
}

interface CanvasWorkspaceBackButtonProps extends Omit<
  ButtonHTMLAttributes<HTMLButtonElement>,
  'children'
> {
  label?: string;
}

export function CanvasWorkspaceBackButton({
  label = '뒤로',
  className,
  ...rest
}: CanvasWorkspaceBackButtonProps) {
  return (
    <Button
      type="button"
      variant="secondary"
      size="sm"
      className={joinClassNames('canvas-workspace-back', 'cursor-pointer', className)}
      {...rest}
    >
      <ArrowLeft aria-hidden="true" className="h-4 w-4" strokeWidth={2} />
      {label}
    </Button>
  );
}

export type CanvasWorkspaceStatusTone = 'complete' | 'editing' | 'locked' | 'neutral';

interface CanvasWorkspaceHeaderProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  title: ReactNode;
  subtitle: ReactNode;
  status?: ReactNode;
  statusTone?: CanvasWorkspaceStatusTone;
}

export function CanvasWorkspaceHeader({
  title,
  subtitle,
  status,
  statusTone = 'neutral',
  className,
  ...rest
}: CanvasWorkspaceHeaderProps) {
  return (
    <header className={joinClassNames('canvas-workspace-header', className)} {...rest}>
      <span
        aria-hidden="true"
        className={joinClassNames(
          'canvas-workspace-header__marker',
          `canvas-workspace-header__marker--${statusTone}`,
        )}
      />
      <div className="canvas-workspace-header__copy">
        <div className="canvas-workspace-header__title">{title}</div>
        <div className="canvas-workspace-header__subtitle">{subtitle}</div>
      </div>
      {status ? (
        <span
          className={joinClassNames(
            'canvas-workspace-header__status',
            `canvas-workspace-header__status--${statusTone}`,
          )}
        >
          {status}
        </span>
      ) : null}
    </header>
  );
}

interface CanvasWorkspacePanelProps extends HTMLAttributes<HTMLElement> {
  children: ReactNode;
  ariaLabel: string;
  animate?: boolean;
}

export function CanvasWorkspacePanel({
  children,
  ariaLabel,
  animate = false,
  className,
  ...rest
}: CanvasWorkspacePanelProps) {
  return (
    <aside
      aria-label={ariaLabel}
      className={joinClassNames(
        'canvas-workspace-panel',
        animate && 'canvas-workspace-panel--animated',
        className,
      )}
      {...rest}
    >
      {children}
    </aside>
  );
}

interface CanvasWorkspacePanelRestoreProps extends Omit<
  ButtonHTMLAttributes<HTMLButtonElement>,
  'children'
> {
  children: ReactNode;
}

export const CanvasWorkspacePanelRestore = forwardRef<
  HTMLButtonElement,
  CanvasWorkspacePanelRestoreProps
>(function CanvasWorkspacePanelRestore({ children, className, ...rest }, ref) {
  return (
    <button
      ref={ref}
      type="button"
      className={joinClassNames('canvas-workspace-panel-restore', className)}
      {...rest}
    >
      {children}
    </button>
  );
});

interface CanvasWorkspaceStateProps extends HTMLAttributes<HTMLDivElement> {
  message: ReactNode;
  actions?: ReactNode;
}

export function CanvasWorkspaceState({
  message,
  actions,
  className,
  ...rest
}: CanvasWorkspaceStateProps) {
  return (
    <div className={joinClassNames('canvas-workspace-state', className)} {...rest}>
      <p>{message}</p>
      {actions ? <div className="canvas-workspace-state__actions">{actions}</div> : null}
    </div>
  );
}
