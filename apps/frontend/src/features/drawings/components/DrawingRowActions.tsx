import { useEffect, useRef, useState } from 'react';
import { Copy, MoreHorizontal, Trash2 } from 'lucide-react';

interface DrawingRowActionsProps {
  label: string;
  open: boolean;
  onToggle: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
}

interface MenuPosition {
  right: number;
  top?: number;
  bottom?: number;
}

const MENU_OFFSET = 4;
const MENU_HEIGHT_ESTIMATE = 96;

function DrawingRowActions({
  label,
  open,
  onToggle,
  onDuplicate,
  onDelete,
}: DrawingRowActionsProps) {
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const [menuPosition, setMenuPosition] = useState<MenuPosition | null>(null);

  const handleToggle = () => {
    if (!open && triggerRef.current !== null) {
      const rect = triggerRef.current.getBoundingClientRect();
      const right = window.innerWidth - rect.right;
      const fitsBelow = rect.bottom + MENU_OFFSET + MENU_HEIGHT_ESTIMATE <= window.innerHeight;
      if (fitsBelow) {
        setMenuPosition({ top: rect.bottom + MENU_OFFSET, right });
      } else {
        setMenuPosition({ bottom: window.innerHeight - rect.top + MENU_OFFSET, right });
      }
    }
    onToggle();
  };

  useEffect(() => {
    if (!open) {
      return;
    }
    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target as Node;
      const insideTrigger = triggerRef.current !== null && triggerRef.current.contains(target);
      const insideMenu = menuRef.current !== null && menuRef.current.contains(target);
      if (!insideTrigger && !insideMenu) {
        onToggle();
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onToggle();
      }
    };
    const closeOnViewportChange = () => {
      onToggle();
    };
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    window.addEventListener('scroll', closeOnViewportChange, true);
    window.addEventListener('resize', closeOnViewportChange);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('scroll', closeOnViewportChange, true);
      window.removeEventListener('resize', closeOnViewportChange);
    };
  }, [open, onToggle]);

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        onClick={handleToggle}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={label}
        className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-text-muted outline-none transition hover:bg-surface hover:text-text-strong focus-visible:ring-2 focus-visible:ring-focus-ring"
      >
        <MoreHorizontal aria-hidden="true" className="h-4 w-4" />
      </button>
      {open && menuPosition !== null ? (
        <div
          ref={menuRef}
          role="menu"
          style={{ right: menuPosition.right, top: menuPosition.top, bottom: menuPosition.bottom }}
          className="fixed z-50 w-32 overflow-hidden rounded-xl border border-line bg-white py-1 shadow-lg"
        >
          <button
            type="button"
            role="menuitem"
            onClick={onDuplicate}
            className="flex w-full items-center gap-2.5 px-4 py-2 text-left text-sm text-ink outline-none transition hover:bg-primary-soft/40 focus-visible:bg-primary-soft/40"
          >
            <Copy aria-hidden="true" className="h-4 w-4 text-text-muted" />
            복제
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={onDelete}
            className="flex w-full items-center gap-2.5 px-4 py-2 text-left text-sm text-danger outline-none transition hover:bg-danger-soft focus-visible:bg-danger-soft"
          >
            <Trash2 aria-hidden="true" className="h-4 w-4" />
            삭제
          </button>
        </div>
      ) : null}
    </>
  );
}

export default DrawingRowActions;
