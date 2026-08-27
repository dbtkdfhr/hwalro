import { type KeyboardEvent as ReactKeyboardEvent, useEffect, useRef, useState } from 'react';
import { Play, SlidersHorizontal, Sparkles, X } from 'lucide-react';
import './LayoutSearchStartDialog.css';

interface Props {
  open: boolean;
  drawingTitle: string;
  starting: boolean;
  errorMessage: string | null;
  onClose: () => void;
  onEditConstraints: () => void;
  onStart: (verify: boolean) => void;
}

export function LayoutSearchStartDialog({
  open,
  drawingTitle,
  starting,
  errorMessage,
  onClose,
  onEditConstraints,
  onStart,
}: Props) {
  const [verify, setVerify] = useState(false);
  const dialogRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!open) {
      setVerify(false);
      return;
    }

    const previouslyFocused =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const animationFrame = window.requestAnimationFrame(() => dialogRef.current?.focus());
    return () => {
      window.cancelAnimationFrame(animationFrame);
      previouslyFocused?.focus();
    };
  }, [open]);

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape') {
      if (!starting) {
        event.preventDefault();
        onClose();
      }
      return;
    }
    if (event.key !== 'Tab') return;

    const focusableElements = Array.from(
      dialogRef.current?.querySelectorAll<HTMLElement>(
        'input:not([disabled]), button:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ) ?? [],
    );
    if (focusableElements.length === 0) {
      event.preventDefault();
      dialogRef.current?.focus();
      return;
    }

    const first = focusableElements[0];
    const last = focusableElements[focusableElements.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  if (!open) return null;

  return (
    <div
      className="app-modal-backdrop layout-search-start-backdrop"
      role="presentation"
      onMouseDown={() => {
        if (!starting) onClose();
      }}
    >
      <section
        ref={dialogRef}
        className="layout-search-start-dialog"
        role="dialog"
        tabIndex={-1}
        aria-modal="true"
        aria-labelledby="layout-search-start-title"
        aria-describedby="layout-search-start-description"
        aria-busy={starting}
        onMouseDown={(event) => event.stopPropagation()}
        onKeyDown={handleKeyDown}
      >
        <header className="layout-search-start-dialog__header">
          <span className="layout-search-start-dialog__icon" aria-hidden="true">
            <Sparkles />
          </span>
          <div>
            <span className="layout-search-start-dialog__eyebrow">LAYOUT SEARCH</span>
            <h2 id="layout-search-start-title">배치 개선안 탐색을 시작할까요?</h2>
            <p id="layout-search-start-description">
              병목과 혼잡을 분석해 더 나은 구조물 배치 후보를 찾습니다.
            </p>
          </div>
          <button
            type="button"
            className="layout-search-start-dialog__close"
            aria-label="닫기"
            disabled={starting}
            onClick={onClose}
          >
            <X aria-hidden="true" />
          </button>
        </header>

        <div className="layout-search-start-dialog__body">
          <section className="layout-search-start-dialog__source" aria-label="탐색 기준 도면">
            <SlidersHorizontal aria-hidden="true" />
            <div>
              <span>저장된 도면 제약 사용</span>
              <strong>{drawingTitle}</strong>
              <p>구조물 이동 범위와 배치 제외 구역을 현재 저장값 기준으로 적용합니다.</p>
            </div>
            <button type="button" disabled={starting} onClick={onEditConstraints}>
              제약 확인·수정
            </button>
          </section>

          <label className="layout-search-start-dialog__verify">
            <input
              type="checkbox"
              checked={verify}
              disabled={starting}
              onChange={(event) => setVerify(event.target.checked)}
            />
            <span>
              <strong>후보마다 시뮬레이션으로 실측 확인</strong>
              <small>정확도가 높아지는 대신 탐색 시간이 더 오래 걸립니다.</small>
            </span>
          </label>

          {errorMessage && (
            <p className="layout-search-start-dialog__error" role="alert">
              {errorMessage}
            </p>
          )}
        </div>

        <footer className="layout-search-start-dialog__footer">
          <button type="button" disabled={starting} onClick={onClose}>
            취소
          </button>
          <button
            type="button"
            className="is-primary"
            disabled={starting}
            onClick={() => onStart(verify)}
          >
            <Play aria-hidden="true" />
            {starting ? '탐색을 시작하는 중...' : '배치 개선안 탐색 시작'}
          </button>
        </footer>
      </section>
    </div>
  );
}
