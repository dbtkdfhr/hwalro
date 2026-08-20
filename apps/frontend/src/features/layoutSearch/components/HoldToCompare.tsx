import { useCallback, useState } from 'react';
import type { SimulationDrawing } from '../../simulations/types';
import { DrawingElements } from './LayoutDiffCanvas';

type CompareView = 'before' | 'after';

interface Props {
  before: SimulationDrawing;
  after: SimulationDrawing;
  changedFabricIds?: ReadonlySet<number>;
}

export function oppositeCompareView(view: CompareView): CompareView {
  return view === 'before' ? 'after' : 'before';
}

export function HoldToCompare({ before, after, changedFabricIds }: Props) {
  const [selectedView, setSelectedView] = useState<CompareView>('after');
  const [temporaryView, setTemporaryView] = useState<CompareView | null>(null);
  const activeView = temporaryView ?? selectedView;

  const selectView = useCallback((view: CompareView) => {
    setTemporaryView(null);
    setSelectedView(view);
  }, []);

  const startPointerHold = useCallback(
    (event: React.PointerEvent<HTMLDivElement>) => {
      if (!event.isPrimary || event.button !== 0) {
        return;
      }
      event.currentTarget.setPointerCapture?.(event.pointerId);
      setTemporaryView(oppositeCompareView(selectedView));
    },
    [selectedView],
  );

  const endPointerHold = useCallback((event: React.PointerEvent<HTMLDivElement>) => {
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    setTemporaryView(null);
  }, []);

  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLDivElement>) => {
      if (!event.repeat && (event.key === ' ' || event.key === 'Enter')) {
        event.preventDefault();
        setTemporaryView(oppositeCompareView(selectedView));
      }
    },
    [selectedView],
  );

  const handleKeyUp = useCallback((event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === ' ' || event.key === 'Enter') {
      event.preventDefault();
      setTemporaryView(null);
    }
  }, []);

  const width = Math.max(before.width, after.width);
  const height = Math.max(before.height, after.height);
  const activeDrawing = activeView === 'after' ? after : before;
  const activeLabel = activeView === 'after' ? '개선 배치' : '기존 배치';

  return (
    <div className="hold-compare">
      <div
        id="layout-comparison-controls"
        className="hold-compare__controls"
        role="group"
        aria-label="표시할 배치 선택"
      >
        <button
          id="compare-before-button"
          type="button"
          aria-pressed={selectedView === 'before'}
          onClick={() => selectView('before')}
        >
          기존 배치
        </button>
        <button
          id="compare-improved-button"
          type="button"
          aria-pressed={selectedView === 'after'}
          onClick={() => selectView('after')}
        >
          개선 배치
        </button>
      </div>
      <div
        className="hold-compare__viewport"
        role="button"
        tabIndex={0}
        aria-label={`현재 ${activeLabel}. 누르고 있는 동안 반대 배치를 표시합니다.`}
        aria-pressed={temporaryView !== null}
        onPointerDown={startPointerHold}
        onPointerUp={endPointerHold}
        onPointerCancel={endPointerHold}
        onLostPointerCapture={() => setTemporaryView(null)}
        onKeyDown={handleKeyDown}
        onKeyUp={handleKeyUp}
        onBlur={() => setTemporaryView(null)}
      >
        <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`${activeLabel} 도면`}>
          <DrawingElements
            drawing={activeDrawing}
            changedFabricIds={activeView === 'after' ? changedFabricIds : undefined}
          />
        </svg>
        <span className="hold-compare__badge">{activeLabel}</span>
        <span className="hold-compare__hint">
          {temporaryView === null
            ? '누르는 동안 반대 배치 보기'
            : '손을 떼면 선택한 배치로 돌아갑니다'}
        </span>
      </div>
    </div>
  );
}
