import type { LayoutZone } from '../../layout/api/layoutMetadataApi';

interface EvacuationRoutePanelProps {
  zone: LayoutZone;
  enabled: boolean;
  loading: boolean;
  errorMessage: string | null;
  onToggle: (enabled: boolean) => void;
}

export function EvacuationRoutePanel({
  zone,
  enabled,
  loading,
  errorMessage,
  onToggle,
}: EvacuationRoutePanelProps) {
  return (
    <section
      className="rounded-lg border border-panel-divider bg-panel-soft p-3 transition-colors"
      aria-labelledby="evacuation-route-title"
    >
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <h3 id="evacuation-route-title" className="text-sm font-bold text-panel-text">
            대피 동선
          </h3>
          <p className="mt-0.5 truncate text-xs text-panel-muted">{zone.name}의 대피 경로 표시</p>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={enabled}
          aria-label={`${zone.name} 대피 동선 표시`}
          aria-busy={loading}
          className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-50 ${
            enabled ? 'bg-primary' : 'bg-line'
          }`}
          onClick={() => onToggle(!enabled)}
          disabled={loading}
        >
          <span
            aria-hidden="true"
            className={`pointer-events-none inline-block size-4.5 rounded-full bg-white shadow-sm ring-0 transition-transform duration-200 ease-in-out ${
              enabled ? 'translate-x-5' : 'translate-x-0.5'
            }`}
          />
        </button>
      </div>
      {loading ? (
        <div className="mt-2 flex items-center gap-1.5 text-xs text-panel-muted" role="status">
          <span className="inline-block size-1.5 animate-pulse rounded-full bg-primary" />
          <span>대피 동선을 불러오는 중...</span>
        </div>
      ) : null}
      {errorMessage !== null ? (
        <p
          className="mt-2 rounded-md border border-danger/40 bg-danger-soft/60 px-2.5 py-1.5 text-xs text-danger"
          role="alert"
        >
          {errorMessage}
        </p>
      ) : null}
    </section>
  );
}
