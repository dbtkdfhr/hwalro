import { FileText, Landmark } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Button, EmptyState, ErrorState, Input, Modal, Skeleton } from '../../../components/ui';
import type { ModalLayer } from '../../../components/ui';
import { useLawDetail } from '../hooks/useLawDetail';
import { useLawSearch } from '../hooks/useLawSearch';
import type { RegulationSummary } from '../types/regulations';
import type { AttachedLawRef } from '../types/risks';

const formatDate = (value: string) =>
  value.length === 8 ? `${value.slice(0, 4)}.${value.slice(4, 6)}.${value.slice(6)}` : value;

const MAX_ATTACHED_LAWS = 10;

function LawArticlePickerModal({
  open,
  onClose,
  selected,
  onConfirm,
  layer = 'default',
}: {
  open: boolean;
  onClose: () => void;
  selected: AttachedLawRef[];
  onConfirm: (refs: AttachedLawRef[]) => void;
  layer?: ModalLayer;
}) {
  const [query, setQuery] = useState('');
  const [activeQuery, setActiveQuery] = useState('');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<RegulationSummary[]>([]);
  const [selectedLaw, setSelectedLaw] = useState<RegulationSummary | null>(null);
  const [checked, setChecked] = useState<AttachedLawRef[]>(selected);
  const [hasMore, setHasMore] = useState(false);
  const lastAppliedPageRef = useRef(0);

  const searchQuery = useLawSearch(activeQuery, page);
  const detailQuery = useLawDetail(selectedLaw?.serialNumber);

  useEffect(() => {
    if (searchQuery.isSuccess && searchQuery.data) {
      const dataPage = searchQuery.data.page;
      if (dataPage === lastAppliedPageRef.current) return;
      lastAppliedPageRef.current = dataPage;
      setItems((prev) =>
        dataPage === 1 ? searchQuery.data.items : [...prev, ...searchQuery.data.items],
      );
      setHasMore(searchQuery.data.hasNext);
    }
  }, [searchQuery.data, searchQuery.isSuccess]);

  const isChecked = (serialNumber: string, articleNumber: string) =>
    checked.some(
      (ref) => ref.lawSerialNumber === serialNumber && ref.lawArticleNumber === articleNumber,
    );

  const toggleArticle = (ref: AttachedLawRef) => {
    setChecked((prev) => {
      const exists = prev.some(
        (item) =>
          item.lawSerialNumber === ref.lawSerialNumber &&
          item.lawArticleNumber === ref.lawArticleNumber,
      );
      if (exists) {
        return prev.filter(
          (item) =>
            !(
              item.lawSerialNumber === ref.lawSerialNumber &&
              item.lawArticleNumber === ref.lawArticleNumber
            ),
        );
      }
      if (prev.length >= MAX_ATTACHED_LAWS) return prev;
      return [...prev, ref];
    });
  };

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextQuery = query.trim();
    if (nextQuery !== activeQuery) {
      setActiveQuery(nextQuery);
      setItems([]);
      setSelectedLaw(null);
      setHasMore(false);
      lastAppliedPageRef.current = 0;
    } else if (searchQuery.isError) {
      void searchQuery.refetch();
      return;
    }
    setPage(1);
  };

  const handleLoadMore = () => {
    if (searchQuery.isError) {
      void searchQuery.refetch();
      return;
    }
    setPage(lastAppliedPageRef.current + 1);
  };

  const handleConfirm = () => {
    onConfirm(checked);
    onClose();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="법령 첨부"
      description="첨부할 법령 조문을 검색해 선택하세요."
      size="lg"
      layer={layer}
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="button" onClick={handleConfirm} disabled={checked.length === 0}>
            {checked.length}/{MAX_ATTACHED_LAWS}개 선택
          </Button>
        </>
      }
    >
      <div className="grid gap-6 md:grid-cols-2">
        <div className="flex min-h-0 flex-col">
          <form onSubmit={handleSearch} className="flex gap-2">
            <Input
              aria-label="법령 검색어"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="법령명으로 검색 (예: 화재, 다중이용업소)"
            />
            <Button type="submit" className="shrink-0">
              검색
            </Button>
          </form>
          <div className="mt-3 flex items-center justify-between">
            <span className="text-xs font-bold tracking-wide text-text-muted">검색 결과</span>
            <span className="text-xs tabular-nums text-text-muted">
              {searchQuery.data?.totalCount ?? 0}건
            </span>
          </div>
          <div className="mt-1.5 max-h-96 min-h-0 flex-1 divide-y divide-line-subtle overflow-y-auto">
            {items.map((item) => {
              const isSelected = selectedLaw?.serialNumber === item.serialNumber;
              return (
                <button
                  key={item.serialNumber}
                  type="button"
                  onClick={() => setSelectedLaw(item)}
                  className={`flex w-full flex-col gap-0.5 border-l-2 px-3.5 py-2.5 text-left transition-colors ${
                    isSelected
                      ? 'border-l-primary bg-primary-soft'
                      : 'border-l-transparent hover:bg-surface'
                  }`}
                >
                  <span className="truncate text-sm font-bold text-text-strong">{item.name}</span>
                  <span className="truncate text-xs text-text-muted">
                    {item.lawType} · 시행 {formatDate(item.effectiveDate)}
                  </span>
                </button>
              );
            })}
            {searchQuery.isPending && items.length === 0 && (
              <div className="space-y-1.5 p-3">
                {[0, 1, 2, 3].map((index) => (
                  <Skeleton key={index} className="h-12 w-full" />
                ))}
              </div>
            )}
            {searchQuery.isError && items.length === 0 && (
              <div className="p-3">
                <ErrorState message="법령 목록을 불러오지 못했습니다." />
              </div>
            )}
            {searchQuery.isError && items.length > 0 && (
              <div className="p-3">
                <ErrorState message="추가 법령 목록을 불러오지 못했습니다. 다시 시도해 주세요." />
              </div>
            )}
            {!searchQuery.isPending && !searchQuery.isError && items.length === 0 && (
              <EmptyState
                icon={Landmark}
                title={activeQuery ? '검색 결과가 없습니다.' : '표시할 법령이 없습니다.'}
                description={
                  activeQuery ? '다른 검색어로 다시 시도해 보세요.' : '잠시 후 다시 시도해 주세요.'
                }
              />
            )}
            {searchQuery.isFetching && items.length > 0 && (
              <p className="px-3.5 py-3 text-center text-xs text-text-muted">불러오는 중...</p>
            )}
          </div>
          {hasMore && (
            <div className="mt-2">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                className="w-full"
                onClick={handleLoadMore}
                disabled={searchQuery.isFetching}
              >
                {searchQuery.isFetching ? '불러오는 중...' : '더 보기'}
              </Button>
            </div>
          )}
        </div>

        <div className="flex min-h-0 flex-col">
          <span className="text-xs font-bold tracking-wide text-text-muted">조문 선택</span>
          <div className="mt-1.5 max-h-96 min-h-0 flex-1 overflow-y-auto">
            {!selectedLaw ? (
              <EmptyState
                icon={FileText}
                title="법령을 선택하세요"
                description="왼쪽 목록에서 법령을 선택하면 조문을 확인할 수 있습니다."
              />
            ) : detailQuery.isPending ? (
              <Skeleton className="h-40 w-full" />
            ) : detailQuery.isError ? (
              <ErrorState message="법령 상세를 불러오지 못했습니다." />
            ) : detailQuery.data ? (
              detailQuery.data.articles.length === 0 ? (
                <EmptyState title="표시할 조문이 없습니다." />
              ) : (
                <ul className="divide-y divide-line-subtle">
                  {detailQuery.data.articles.map((article, index) =>
                    article.section ? (
                      <li
                        key={`${article.number}-${article.title}-${index}`}
                        className="bg-surface/60 px-3.5 py-2 text-xs font-bold text-text-muted"
                      >
                        {article.content}
                      </li>
                    ) : (
                      <li key={`${article.number}-${article.title}-${index}`}>
                        <div className="flex items-center">
                          <label
                            className={`flex min-w-0 flex-1 cursor-pointer items-start gap-2.5 px-3.5 py-2.5 transition-colors ${
                              isChecked(selectedLaw.serialNumber, article.number)
                                ? 'bg-primary-soft'
                                : 'hover:bg-surface'
                            }`}
                          >
                            <input
                              type="checkbox"
                              className="mt-0.5 h-4 w-4 shrink-0 accent-primary"
                              checked={isChecked(selectedLaw.serialNumber, article.number)}
                              onChange={() =>
                                toggleArticle({
                                  lawSerialNumber: selectedLaw.serialNumber,
                                  lawArticleNumber: article.number,
                                })
                              }
                            />
                            <span className="text-sm font-bold text-text-strong">
                              제{article.number}조{article.title ? ` (${article.title})` : ''}
                            </span>
                          </label>
                          <a
                            href={`/regulations?serialNumber=${encodeURIComponent(selectedLaw.serialNumber)}&query=${encodeURIComponent(selectedLaw.name)}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="mr-3 shrink-0 rounded-md px-2 py-1 text-xs font-bold text-primary outline-none transition-colors hover:bg-primary-soft focus-visible:ring-2 focus-visible:ring-focus-ring"
                          >
                            상세보기
                          </a>
                        </div>
                      </li>
                    ),
                  )}
                </ul>
              )
            ) : null}
          </div>
        </div>
      </div>
    </Modal>
  );
}

export default LawArticlePickerModal;
