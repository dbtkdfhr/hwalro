const PAGE_BUTTON_COUNT = 5;

export interface PaginationProps {
  page: number;
  pageCount: number;
  onPageChange: (page: number) => void;
  disabled?: boolean;
  ariaLabel?: string;
}

const navButtonClassName =
  'h-9 cursor-pointer rounded-lg border border-line px-3 text-sm font-bold text-text-strong outline-none transition hover:bg-surface focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-40';

function Pagination({
  page,
  pageCount,
  onPageChange,
  disabled = false,
  ariaLabel = '목록 페이지',
}: PaginationProps) {
  const safePage = Math.min(Math.max(page, 1), pageCount);
  const pageGroupStart = Math.floor((safePage - 1) / PAGE_BUTTON_COUNT) * PAGE_BUTTON_COUNT + 1;
  const pageGroupEnd = Math.min(pageGroupStart + PAGE_BUTTON_COUNT - 1, pageCount);
  const pageNumbers = Array.from(
    { length: pageGroupEnd - pageGroupStart + 1 },
    (_, index) => pageGroupStart + index,
  );

  return (
    <nav className="flex items-center justify-center gap-2" aria-label={ariaLabel}>
      <button
        type="button"
        onClick={() => onPageChange(safePage - 1)}
        disabled={disabled || safePage === 1}
        className={navButtonClassName}
      >
        이전
      </button>
      {pageNumbers.map((pageNumber) => (
        <button
          key={pageNumber}
          type="button"
          onClick={() => onPageChange(pageNumber)}
          disabled={disabled}
          aria-current={pageNumber === safePage ? 'page' : undefined}
          aria-label={`${pageNumber}페이지`}
          className={`h-9 min-w-9 cursor-pointer rounded-lg px-2 text-sm font-bold tabular-nums outline-none transition focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-40 ${
            pageNumber === safePage
              ? 'bg-primary text-white'
              : 'border border-line text-text-strong hover:bg-surface'
          }`}
        >
          {pageNumber}
        </button>
      ))}
      <button
        type="button"
        onClick={() => onPageChange(safePage + 1)}
        disabled={disabled || safePage === pageCount}
        className={navButtonClassName}
      >
        다음
      </button>
    </nav>
  );
}

export default Pagination;
