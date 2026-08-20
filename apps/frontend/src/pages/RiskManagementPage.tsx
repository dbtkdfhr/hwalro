import { useEffect, useState } from 'react';
import { ShieldAlert } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Input,
  PageHeader,
  Pagination,
  Skeleton,
} from '../components/ui';
import { useDebounce } from '../hooks/useDebounce';
import { useRiskDetail, useRiskList } from '../features/risks/hooks/useRiskList';
import { getRiskErrorMessage } from '../features/risks/utils/getRiskErrorMessage';
import RiskCreateDialog from './riskManagement/RiskCreateDialog';
import RiskDetailPanel from './riskManagement/RiskDetailPanel';
import RiskItemTable from './riskManagement/RiskItemTable';

const PAGE_SIZE = 5;

function parseRiskId(value: string | null): number | null {
  if (value === null) return null;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function RiskManagementPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(1);
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebounce(query, 300);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const { items, totalCount, isPending, isError, error } = useRiskList(
    page,
    PAGE_SIZE,
    debouncedQuery,
  );
  const linkedRiskId = parseRiskId(searchParams.get('riskId'));
  const linkedRiskInCurrentPage = items.find((item) => item.id === linkedRiskId) ?? null;
  const linkedRiskQuery = useRiskDetail(linkedRiskId, linkedRiskInCurrentPage === null);

  const pageCount = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  const clearLinkedRisk = () => {
    if (!searchParams.has('riskId')) return;
    const nextSearchParams = new URLSearchParams(searchParams);
    nextSearchParams.delete('riskId');
    setSearchParams(nextSearchParams, { replace: true });
  };

  const handleSelect = (id: number) => {
    clearLinkedRisk();
    setSelectedId(id);
  };

  const handlePageChange = (nextPage: number) => {
    clearLinkedRisk();
    setSelectedId(null);
    setPage(nextPage);
  };

  useEffect(() => {
    if (!isPending && !isError && page > pageCount) {
      setSelectedId(null);
      setPage(pageCount);
    }
  }, [isPending, isError, page, pageCount]);

  const selectedItem =
    linkedRiskId !== null
      ? (linkedRiskInCurrentPage ?? linkedRiskQuery.data ?? null)
      : (items.find((item) => item.id === selectedId) ?? items[0] ?? null);
  const isLinkedRiskPending =
    linkedRiskId !== null && linkedRiskInCurrentPage === null && linkedRiskQuery.isPending;
  const linkedRiskError =
    linkedRiskId !== null && linkedRiskInCurrentPage === null && linkedRiskQuery.isError
      ? linkedRiskQuery.error
      : null;

  return (
    <main className="bg-background">
      <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
        <div className="border-b border-line pb-6">
          <PageHeader
            eyebrow="안전 운영"
            title="위험 예상 항목 관리"
            description="시뮬레이션과 현장 점검에서 발견한 위험을 담당자와 상태로 관리합니다."
            actions={
              <Button size="lg" onClick={() => setIsCreateOpen(true)}>
                위험 예상 항목 등록
              </Button>
            }
          />
        </div>

        <Card className="mt-5" aria-label="위험 예상 항목 검색">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
            <label htmlFor="risk-search" className="sr-only">
              위험 항목 검색
            </label>
            <Input
              id="risk-search"
              type="search"
              value={query}
              onChange={(event) => {
                clearLinkedRisk();
                setSelectedId(null);
                setQuery(event.target.value);
                setPage(1);
              }}
              placeholder="위험 항목 검색"
              className="min-w-0 flex-1"
            />
          </div>
        </Card>

        <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_300px]">
          <Card padded={false} className="flex min-h-[500px] flex-col overflow-hidden">
            <div className="border-b border-line px-5 py-4 sm:px-7">
              <h2 className="text-xl font-black text-ink">위험 예상 목록</h2>
            </div>
            <div className="flex min-h-0 flex-1 flex-col">
              {isPending ? (
                <ul className="divide-y divide-line">
                  {Array.from({ length: 4 }).map((_, index) => (
                    <li
                      key={index}
                      className="grid grid-cols-[minmax(0,2.5fr)_minmax(0,1.5fr)_1fr_1fr_2fr] items-center gap-x-6 px-5 py-4 sm:px-7"
                    >
                      <Skeleton className="h-4 w-3/4" />
                      <Skeleton className="h-4 w-16" />
                      <Skeleton className="h-4 w-12" />
                      <Skeleton className="h-4 w-16" />
                      <Skeleton className="h-4 w-20" />
                    </li>
                  ))}
                </ul>
              ) : isError ? (
                <div className="p-5 sm:p-7">
                  <ErrorState message={getRiskErrorMessage(error)} />
                </div>
              ) : (
                <>
                  <RiskItemTable
                    items={items}
                    selectedId={selectedItem?.id ?? null}
                    onSelect={handleSelect}
                    emptyTitle={
                      debouncedQuery.trim()
                        ? '검색 결과가 없습니다.'
                        : '등록된 위험 항목이 없습니다.'
                    }
                    emptyDescription={
                      debouncedQuery.trim() ? '다른 검색어로 위험 항목을 검색해 보세요.' : undefined
                    }
                  />
                  {items.length > 0 && (
                    <div className="mt-auto flex flex-col items-center justify-between gap-3 border-t border-line px-5 py-3 sm:flex-row">
                      <p className="text-sm tabular-nums text-text-muted">
                        총 {totalCount.toLocaleString()}건
                      </p>
                      <Pagination
                        page={page}
                        pageCount={pageCount}
                        onPageChange={handlePageChange}
                        ariaLabel="위험 예상 목록 페이지"
                      />
                    </div>
                  )}
                </>
              )}
            </div>
          </Card>

          <Card padded={false} className="flex min-h-[500px] flex-col p-6">
            <h2 className="text-xl font-black text-ink">위험 상세</h2>
            <div className="mt-5">
              {isLinkedRiskPending ? (
                <div className="space-y-4">
                  <Skeleton className="h-10 w-full" />
                  <Skeleton className="h-24 w-full" />
                  <Skeleton className="h-40 w-full" />
                </div>
              ) : linkedRiskError ? (
                <ErrorState message={getRiskErrorMessage(linkedRiskError)} />
              ) : selectedItem ? (
                <RiskDetailPanel key={selectedItem.id} risk={selectedItem} />
              ) : (
                <EmptyState icon={ShieldAlert} title="선택된 위험 항목이 없습니다." />
              )}
            </div>
          </Card>
        </div>

        {isCreateOpen && <RiskCreateDialog onClose={() => setIsCreateOpen(false)} />}
      </div>
    </main>
  );
}

export default RiskManagementPage;
