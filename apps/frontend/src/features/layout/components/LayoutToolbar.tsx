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
        <h2>도면 설정</h2>
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
          className={`layout-panel-actions__save ${saveStatus === 'error' ? 'is-error' : ''}`}
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
          className="layout-panel-actions__simulation"
        >
          시뮬레이션 배치
        </button>
      </div>
    </div>
  );
}
