import type { Ref } from 'react';
import { Minus } from 'lucide-react';

interface LayoutToolbarProps {
  saveStatus: 'idle' | 'saving' | 'saved' | 'error';
  onSave: () => void;
  onStartSimulation: () => void;
  readOnly: boolean;
  onCollapse: () => void;
  collapseButtonRef?: Ref<HTMLButtonElement>;
}

export function LayoutToolbar({
  saveStatus,
  onSave,
  onStartSimulation,
  readOnly,
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
        <button
          type="button"
          onClick={onSave}
          disabled={readOnly}
          className={`h-9 rounded-lg text-sm font-bold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-45 ${
            saveStatus === 'error'
              ? 'bg-panel-danger text-white'
              : 'bg-panel-accent text-ink hover:opacity-85'
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
        {readOnly && (
          <p className="rounded-md bg-panel-soft px-2 py-1.5 text-xs leading-4 text-panel-muted">
            시뮬레이션에 사용된 버전으로, 도면 편집이 잠겨 있습니다.
          </p>
        )}
        <button
          type="button"
          onClick={onStartSimulation}
          className="h-9 rounded-lg border border-panel-accent text-sm font-bold text-panel-accent transition-colors hover:bg-panel-accent/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
        >
          시뮬레이션 배치
        </button>
      </div>
    </div>
  );
}
