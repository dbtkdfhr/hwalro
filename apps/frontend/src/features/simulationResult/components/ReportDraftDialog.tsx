import { type KeyboardEvent as ReactKeyboardEvent, useEffect, useRef, useState } from 'react';
import { Check, FileText, Loader2, Sparkles, X } from 'lucide-react';
import { Pagination } from '../../../components/ui';
import {
  COMPARABLE_SIMULATION_PAGE_SIZE,
  useComparableSimulations,
} from '../hooks/useComparableSimulations';
import type { SimulationResultViewModel } from '../types';

const MAX_COMPARISON_COUNT = 5;

function formatDuration(seconds: number): string {
  return `${seconds.toLocaleString('ko-KR', { maximumFractionDigits: 1 })}초`;
}

interface Props {
  open: boolean;
  result: SimulationResultViewModel;
  isGenerating: boolean;
  errorMessage: string | null;
  onClose: () => void;
  onGenerate: (comparisonResultIds: number[]) => void;
}

export function ReportDraftDialog({
  open,
  result,
  isGenerating,
  errorMessage,
  onClose,
  onGenerate,
}: Props) {
  const [selectedComparisons, setSelectedComparisons] = useState<number[]>([]);
  const [comparisonPage, setComparisonPage] = useState(1);
  const dialogRef = useRef<HTMLElement>(null);
  const comparableQuery = useComparableSimulations(
    Number(result.simulationId),
    comparisonPage,
    open,
  );
  const comparisonPageCount = comparableQuery.data
    ? Math.max(1, Math.ceil(comparableQuery.data.totalCount / COMPARABLE_SIMULATION_PAGE_SIZE))
    : comparisonPage;

  useEffect(() => {
    if (!open) {
      setSelectedComparisons([]);
      setComparisonPage(1);
    }
  }, [open, result.simulationId]);

  useEffect(() => {
    if (comparableQuery.data && comparisonPage > comparisonPageCount) {
      setComparisonPage(comparisonPageCount);
    }
  }, [comparableQuery.data, comparisonPage, comparisonPageCount]);

  useEffect(() => {
    if (!open) return;
    const previouslyFocused =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const animationFrame = window.requestAnimationFrame(() => {
      const firstComparison = dialogRef.current?.querySelector<HTMLInputElement>(
        'input[type="checkbox"]:not([disabled])',
      );
      (firstComparison ?? dialogRef.current)?.focus();
    });
    return () => {
      window.cancelAnimationFrame(animationFrame);
      previouslyFocused?.focus();
    };
  }, [open]);

  const handleDialogKeyDown = (event: ReactKeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape') {
      if (!isGenerating) {
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

  const toggleComparison = (simulationResultId: number) => {
    setSelectedComparisons((values) =>
      values.includes(simulationResultId)
        ? values.filter((value) => value !== simulationResultId)
        : [...values, simulationResultId],
    );
  };

  if (!open) return null;

  return (
    <div
      className="dialog-backdrop"
      role="presentation"
      onMouseDown={() => {
        if (!isGenerating) onClose();
      }}
    >
      <section
        ref={dialogRef}
        className="report-dialog"
        role="dialog"
        tabIndex={-1}
        aria-modal="true"
        aria-labelledby="report-dialog-title"
        aria-describedby="report-dialog-description"
        aria-busy={isGenerating}
        onMouseDown={(event) => event.stopPropagation()}
        onKeyDown={handleDialogKeyDown}
      >
        <header className="report-dialog-header">
          <div className="report-dialog-heading">
            <span className="report-dialog-heading-icon" aria-hidden="true">
              <Sparkles />
            </span>
            <div>
              <h2 id="report-dialog-title">AI 보고서 초안 만들기</h2>
              <p id="report-dialog-description">
                현재 결과를 기준으로 비교할 시뮬레이션을 선택하세요.
              </p>
            </div>
          </div>
          <button
            type="button"
            className="report-dialog-close"
            aria-label="닫기"
            disabled={isGenerating}
            onClick={onClose}
          >
            <X aria-hidden="true" />
          </button>
        </header>

        <div className="report-dialog-body">
          <section className="current-simulation" aria-label="보고서 분석 기준">
            <span className="current-simulation-icon" aria-hidden="true">
              <FileText />
            </span>
            <div>
              <span>분석 기준</span>
              <strong>{result.title}</strong>
              <small>{result.subtitle}</small>
            </div>
            <em>필수</em>
          </section>

          <section className="report-comparison-section" aria-labelledby="comparison-title">
            <div className="report-comparison-heading">
              <div>
                <h3 id="comparison-title">비교 시뮬레이션</h3>
                <p>선택하지 않으면 현재 결과만으로 초안을 생성합니다.</p>
              </div>
              <span aria-live="polite">
                {selectedComparisons.length} / {MAX_COMPARISON_COUNT}
              </span>
            </div>

            {comparableQuery.isPending ? (
              <div className="report-comparison-empty" role="status">
                <strong>비교 시뮬레이션을 불러오는 중입니다</strong>
                <span>최신 실행 결과부터 확인하고 있습니다.</span>
              </div>
            ) : comparableQuery.isError ? (
              <div className="report-comparison-empty" role="alert">
                <strong>비교 시뮬레이션을 불러오지 못했습니다</strong>
                <button
                  type="button"
                  className="report-comparison-retry cursor-pointer"
                  disabled={comparableQuery.isFetching}
                  onClick={() => void comparableQuery.refetch()}
                >
                  {comparableQuery.isFetching && (
                    <Loader2 className="animate-spin" aria-hidden="true" />
                  )}
                  다시 시도
                </button>
              </div>
            ) : comparableQuery.data.items.length > 0 ? (
              <>
                <div className="report-comparison-list">
                  {comparableQuery.data.items.map((item) => {
                    const comparisonResultId = item.simulationResultId;
                    const isSelected = selectedComparisons.includes(comparisonResultId);
                    return (
                      <label
                        key={item.id}
                        className={`report-comparison-option ${isSelected ? 'is-selected' : ''}`}
                      >
                        <input
                          type="checkbox"
                          checked={isSelected}
                          disabled={
                            isGenerating ||
                            (!isSelected && selectedComparisons.length >= MAX_COMPARISON_COUNT)
                          }
                          onChange={() => toggleComparison(comparisonResultId)}
                        />
                        <span className="report-comparison-check" aria-hidden="true">
                          <Check />
                        </span>
                        <span className="report-comparison-copy">
                          <strong>{item.name}</strong>
                          <small>총 대피 시간 {formatDuration(item.totalEvacuationTime)}</small>
                        </span>
                      </label>
                    );
                  })}
                </div>
                {comparisonPageCount > 1 && (
                  <div className="report-comparison-pagination">
                    <Pagination
                      page={comparisonPage}
                      pageCount={comparisonPageCount}
                      onPageChange={setComparisonPage}
                      disabled={isGenerating || comparableQuery.isFetching}
                      ariaLabel="비교 시뮬레이션 페이지"
                    />
                  </div>
                )}
              </>
            ) : (
              <div className="report-comparison-empty">
                <strong>비교 가능한 시뮬레이션이 없습니다</strong>
                <span>현재 결과만으로 AI 보고서 초안을 생성할 수 있습니다.</span>
              </div>
            )}
          </section>

          {errorMessage && (
            <p className="report-dialog-error" role="alert">
              {errorMessage}
            </p>
          )}
        </div>

        <footer className="report-dialog-footer">
          <p>
            <Sparkles aria-hidden="true" />
            선택한 결과는 초안의 비교 분석에 반영됩니다.
          </p>
          <div className="dialog-actions">
            <button type="button" disabled={isGenerating} onClick={onClose}>
              취소
            </button>
            <button
              type="button"
              disabled={isGenerating}
              onClick={() => onGenerate(selectedComparisons)}
            >
              {isGenerating ? (
                <>
                  <Loader2 aria-hidden="true" className="report-dialog-spinner" />
                  초안 생성 중
                </>
              ) : (
                <>
                  <Sparkles aria-hidden="true" />
                  AI 초안 생성
                </>
              )}
            </button>
          </div>
        </footer>
      </section>
    </div>
  );
}
