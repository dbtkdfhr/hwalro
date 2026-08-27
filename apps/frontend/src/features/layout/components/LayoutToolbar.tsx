import type { Ref } from 'react';
import { History, Minus, ShieldAlert } from 'lucide-react';

interface LayoutToolbarProps {
  onOpenHistory: () => void;
  readOnly: boolean;
  riskMode?: boolean;
  onToggleRiskMode?: () => void;
  onCollapse: () => void;
  collapseButtonRef?: Ref<HTMLButtonElement>;
}

export function LayoutToolbar({
  onOpenHistory,
  readOnly,
  riskMode = false,
  onToggleRiskMode,
  onCollapse,
  collapseButtonRef,
}: LayoutToolbarProps) {
  return (
    <div className="layout-panel-actions">
      <div className="layout-panel-actions__header">
        <div>
          <small>LAYOUT EDITOR</small>
          <h2>도면 설정</h2>
        </div>
        <button
          ref={collapseButtonRef}
          type="button"
          onClick={onCollapse}
          aria-controls="layout-settings-panel"
          aria-expanded="true"
          aria-label="도면 설정 최소화"
          className="layout-panel-actions__collapse"
        >
          <Minus aria-hidden="true" />
        </button>
      </div>
      <div className="layout-panel-actions__buttons">
        {readOnly && (
          <p className="rounded-md bg-panel-soft px-2 py-1.5 text-xs leading-4 text-panel-muted">
            시뮬레이션에 사용된 버전으로, 도면 편집이 잠겨 있습니다.
          </p>
        )}
        <button
          type="button"
          onClick={onOpenHistory}
          className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-panel-border bg-panel-soft text-sm font-bold text-panel-text transition-colors hover:bg-panel focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
        >
          <History aria-hidden="true" className="h-4 w-4" />
          버전 이력
        </button>
        {onToggleRiskMode && (
          <button
            type="button"
            onClick={onToggleRiskMode}
            aria-pressed={riskMode}
            title={
              riskMode
                ? '도면을 드래그해 주의 구역을 지정하세요. ESC로 종료합니다.'
                : '도면 위에 주의 구역을 표시합니다.'
            }
            className={`flex h-9 items-center justify-center gap-1.5 rounded-lg border text-sm font-bold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring ${
              riskMode
                ? 'border-danger bg-danger-soft text-danger-strong'
                : 'border-panel-border bg-panel-soft text-panel-text hover:bg-panel'
            }`}
          >
            <ShieldAlert aria-hidden="true" className="h-4 w-4" />
            {riskMode ? '주의 구역 지정 중' : '주의 구역'}
          </button>
        )}
      </div>
    </div>
  );
}

interface LayoutPrimaryActionsProps {
  saveStatus: 'idle' | 'saving' | 'saved' | 'error';
  onSave: () => void;
  onStartSimulation: () => void;
  readOnly: boolean;
}

export function LayoutPrimaryActions({
  saveStatus,
  onSave,
  onStartSimulation,
  readOnly,
}: LayoutPrimaryActionsProps) {
  return (
    <div className="layout-primary-actions">
      <button
        type="button"
        onClick={onSave}
        disabled={readOnly}
        className={`h-9 rounded-lg text-sm font-bold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-45 ${
          saveStatus === 'error'
            ? 'bg-panel-danger text-white'
            : 'bg-panel-accent text-white hover:opacity-85'
        }`}
      >
        {saveStatus === 'saving'
          ? '저장 중'
          : saveStatus === 'saved'
            ? '저장 완료'
            : saveStatus === 'error'
              ? '저장 실패'
              : '저장'}
      </button>
      <button
        type="button"
        onClick={onStartSimulation}
        className="h-9 rounded-lg border border-panel-accent text-sm font-bold text-panel-accent transition-colors hover:bg-panel-accent/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
      >
        시뮬레이션 배치
      </button>
    </div>
  );
}
