import type { CollapsibleWorkspacePanelController } from '../../../components/workspace';
import { Minus } from 'lucide-react';

interface Props {
  panel: CollapsibleWorkspacePanelController;
  onCompare: () => void;
}

export function ImprovementComparisonPanel({ panel, onCompare }: Props) {
  if (panel.isMinimized) {
    return (
      <button type="button" className="improvement-action-restore" onClick={panel.restore}>
        배치 개선안 찾기
      </button>
    );
  }

  return (
    <section
      className={`improvement-action floating-surface ${panel.isCollapsing ? 'is-collapsing' : ''} ${panel.isExpanding ? 'is-expanding' : ''}`}
      onAnimationEnd={(event) => {
        if (event.currentTarget === event.target) panel.handleAnimationEnd();
      }}
    >
      <div className="improvement-action-header">
        <strong>배치 개선안 찾기</strong>
        <button
          type="button"
          className="improvement-collapse"
          aria-label="배치 개선안 찾기 최소화"
          onClick={panel.collapse}
        >
          <Minus aria-hidden="true" />
        </button>
      </div>
      <p>병목·혼잡·출구 편중을 진단하고, 그 원인을 겨냥한 배치 변경 후보를 제시합니다.</p>
      <button type="button" onClick={onCompare}>
        배치 개선안 찾기
      </button>
    </section>
  );
}
