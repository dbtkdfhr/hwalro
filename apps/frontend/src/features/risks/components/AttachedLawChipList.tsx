import { X } from 'lucide-react';
import { useLawDetail } from '../hooks/useLawDetail';
import type { RegulationDetail } from '../types/regulations';
import type { AttachedLawRef } from '../types/risks';

function buildArticleLabel(
  serialNumber: string,
  articleNumber: string,
  detail: RegulationDetail | undefined,
): string {
  if (!detail) return `법령 ${serialNumber} 제${articleNumber}조`;
  const article = detail.articles.find((item) => item.number === articleNumber);
  const title = article?.title;
  return `${detail.name} 제${articleNumber}조${title ? ` (${title})` : ''}`;
}

function Chip({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="inline-flex min-w-0 max-w-full items-center gap-1.5 rounded-md border border-line-strong bg-surface-sunken px-2 py-1 text-xs font-bold text-text-strong shadow-neu-pressed">
      <span className="min-w-0 truncate">{label}</span>
      <button
        type="button"
        onClick={onRemove}
        aria-label="첨부 조문 제거"
        className="flex h-4 w-4 shrink-0 items-center justify-center rounded bg-surface-raised text-text-muted shadow-neu-raised transition-[background-color,color,box-shadow] hover:bg-surface-overlay hover:text-text-strong active:shadow-neu-pressed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
      >
        <X aria-hidden="true" className="h-3 w-3" />
      </button>
    </span>
  );
}

function LawDetailChips({
  serialNumber,
  articleNumbers,
  names,
  onRemove,
}: {
  serialNumber: string;
  articleNumbers: string[];
  names?: ReadonlyMap<string, string>;
  onRemove: (ref: AttachedLawRef) => void;
}) {
  const detailQuery = useLawDetail(serialNumber);
  return (
    <>
      {articleNumbers.map((articleNumber) => {
        const key = `${serialNumber}:${articleNumber}`;
        const label =
          names?.get(key) ??
          (detailQuery.isError
            ? `법령 정보를 불러올 수 없습니다 (${serialNumber} 제${articleNumber}조)`
            : buildArticleLabel(serialNumber, articleNumber, detailQuery.data));
        return (
          <Chip
            key={articleNumber}
            label={label}
            onRemove={() =>
              onRemove({ lawSerialNumber: serialNumber, lawArticleNumber: articleNumber })
            }
          />
        );
      })}
    </>
  );
}

export function AttachedLawChipList({
  refs,
  names,
  onRemove,
  className,
}: {
  refs: AttachedLawRef[];
  names?: ReadonlyMap<string, string>;
  onRemove: (ref: AttachedLawRef) => void;
  className?: string;
}) {
  const grouped = new Map<string, string[]>();
  for (const ref of refs) {
    const articles = grouped.get(ref.lawSerialNumber);
    if (articles) articles.push(ref.lawArticleNumber);
    else grouped.set(ref.lawSerialNumber, [ref.lawArticleNumber]);
  }

  return (
    <div className={`flex flex-wrap gap-2 ${className ?? ''}`}>
      {[...grouped.entries()].map(([serialNumber, articleNumbers]) => (
        <LawDetailChips
          key={serialNumber}
          serialNumber={serialNumber}
          articleNumbers={articleNumbers}
          names={names}
          onRemove={onRemove}
        />
      ))}
    </div>
  );
}
