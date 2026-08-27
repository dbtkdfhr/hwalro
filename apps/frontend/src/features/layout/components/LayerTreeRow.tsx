import { ChevronRight } from 'lucide-react';
import {
  useEffect,
  useRef,
  useState,
  type DragEvent,
  type KeyboardEvent,
  type MouseEvent,
  type ReactNode,
} from 'react';
import type { DropPosition } from '../utils/layerDrop';
import { LayerContextMenu, type MenuItem } from './LayerContextMenu';

export type LayerMenuEntry = MenuItem | { label: string; children: MenuItem[] };
export type LayerDropFeedback = DropPosition | 'inside';

interface LayerTreeRowProps {
  label: string;
  /** 종류를 한눈에 구분하기 위한 아이콘. 행 왼쪽에 고정 폭으로 놓인다. */
  icon?: ReactNode;
  detail?: string;
  selected: boolean;
  indent?: boolean;
  ariaLabel: string;
  onSelect: (additive: boolean) => void;
  onDoubleClick?: () => void;
  expanded?: boolean;
  onToggleExpanded?: () => void;
  draggable?: boolean;
  onDragStart?: (event: DragEvent) => void;
  onDragEnd?: () => void;
  dropFeedbackFor?: (event: DragEvent) => LayerDropFeedback | null;
  onDrop?: (event: DragEvent, feedback: LayerDropFeedback) => void;
  menuItems?: LayerMenuEntry[];
}

// 삽입선은 드래그 중 한 행에만 보여야 한다. 행마다 지역 상태만 두면 다른 행으로 넘어갈 때
// 이전 행의 dragleave가 누락돼 선이 겹쳐 남는다.
let clearActiveRow: (() => void) | null = null;

const rowClassName =
  'layout-layer-row__button flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm text-panel-text transition-colors hover:bg-panel-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring';

export function LayerTreeRow({
  label,
  icon,
  detail,
  selected,
  indent,
  ariaLabel,
  onSelect,
  onDoubleClick,
  expanded,
  onToggleExpanded,
  draggable = false,
  onDragStart,
  onDragEnd,
  dropFeedbackFor,
  onDrop,
  menuItems,
}: LayerTreeRowProps) {
  const rowRef = useRef<HTMLButtonElement>(null);
  const dropFeedbackRef = useRef<LayerDropFeedback | null>(null);
  const [dropFeedback, setDropFeedback] = useState<LayerDropFeedback | null>(null);
  const [menuAnchor, setMenuAnchor] = useState<{ x: number; y: number } | null>(null);
  const hasMenu = Boolean(menuItems?.length);

  const openMenu = (x: number, y: number) => {
    if (!hasMenu) return;
    if (!selected) onSelect(false);
    setMenuAnchor({ x, y });
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if ((event.key === 'F10' && event.shiftKey) || event.key === 'ContextMenu') {
      event.preventDefault();
      const rect = event.currentTarget.getBoundingClientRect();
      openMenu(rect.left, rect.bottom);
    }
  };

  // 렌더마다 새로 만들면 clearActiveRow 동일성 비교가 깨지므로 한 번만 만든다.
  const clearDropFeedback = useRef(() => {
    dropFeedbackRef.current = null;
    setDropFeedback(null);
    if (clearActiveRow === clearDropFeedback) clearActiveRow = null;
  }).current;

  const updateDropFeedback = (event: DragEvent<HTMLElement>) => {
    const feedback = dropFeedbackFor?.(event) ?? null;
    if (feedback === null) return;
    event.preventDefault();
    if (clearActiveRow && clearActiveRow !== clearDropFeedback) clearActiveRow();
    clearActiveRow = clearDropFeedback;
    dropFeedbackRef.current = feedback;
    setDropFeedback(feedback);
  };

  // 캔버스에서 선택하면 목록이 그 행까지 따라간다.
  useEffect(() => {
    if (selected) rowRef.current?.scrollIntoView({ block: 'nearest' });
  }, [selected]);

  // 드래그가 어디서 끝나든(취소·바깥 드롭 포함) 삽입선을 지운다.
  useEffect(() => {
    window.addEventListener('dragend', clearDropFeedback);
    window.addEventListener('drop', clearDropFeedback);
    return () => {
      window.removeEventListener('dragend', clearDropFeedback);
      window.removeEventListener('drop', clearDropFeedback);
    };
  }, [clearDropFeedback]);

  return (
    <li
      className="layout-layer-row"
      data-drop-position={dropFeedback ?? undefined}
      onDragEnter={updateDropFeedback}
      onDragOver={updateDropFeedback}
      onDragLeave={(event) => {
        const rect = event.currentTarget.getBoundingClientRect();
        const isOutside =
          event.clientX <= rect.left ||
          event.clientX >= rect.right ||
          event.clientY <= rect.top ||
          event.clientY >= rect.bottom;
        if (isOutside) clearDropFeedback();
      }}
      onDrop={(event) => {
        const feedback = dropFeedbackRef.current ?? dropFeedbackFor?.(event) ?? null;
        event.stopPropagation();
        clearDropFeedback();
        if (feedback !== null) onDrop?.(event, feedback);
      }}
    >
      {dropFeedback === 'before' || dropFeedback === 'after' ? (
        <span
          className={`layout-layer-insertion layout-layer-insertion--${dropFeedback}`}
          aria-hidden
        />
      ) : null}
      {dropFeedback ? (
        <span role="status" className="sr-only">
          {dropFeedback === 'before'
            ? `${label} 앞에 삽입`
            : dropFeedback === 'after'
              ? `${label} 뒤에 삽입`
              : `${label} 안으로 이동`}
        </span>
      ) : null}
      {onToggleExpanded ? (
        <button
          type="button"
          className="layout-layer-row__toggle"
          aria-label={`${label} ${expanded ? '접기' : '펴기'}`}
          aria-expanded={expanded}
          onClick={(event) => {
            event.stopPropagation();
            onToggleExpanded();
          }}
        >
          <ChevronRight aria-hidden className={expanded ? 'is-expanded' : ''} />
        </button>
      ) : null}
      <button
        ref={rowRef}
        type="button"
        aria-label={ariaLabel}
        aria-current={selected}
        aria-haspopup={hasMenu ? 'menu' : undefined}
        onClick={(event) => onSelect(event.shiftKey || event.ctrlKey || event.metaKey)}
        onDoubleClick={onDoubleClick}
        onContextMenu={(event: MouseEvent<HTMLButtonElement>) => {
          if (!hasMenu) return;
          event.preventDefault();
          openMenu(event.clientX, event.clientY);
        }}
        onKeyDown={handleKeyDown}
        draggable={draggable}
        onDragStart={onDragStart}
        onDragEnd={() => {
          clearDropFeedback();
          onDragEnd?.();
        }}
        className={`${rowClassName} ${selected ? 'is-selected font-bold' : ''} ${indent ? 'pl-6' : ''} ${onToggleExpanded ? 'layout-layer-row__button--toggleable' : ''} ${dropFeedback === 'inside' ? 'ring-2 ring-focus-ring' : ''}`}
      >
        {icon}
        <span className="min-w-0 flex-1 truncate">{label}</span>
        {detail ? <span className="shrink-0 text-[11px] text-text-muted">{detail}</span> : null}
      </button>
      {menuAnchor !== null && menuItems ? (
        <LayerContextMenu
          anchor={menuAnchor}
          items={menuItems}
          onClose={() => {
            setMenuAnchor(null);
            window.requestAnimationFrame(() => rowRef.current?.focus());
          }}
        />
      ) : null}
    </li>
  );
}
