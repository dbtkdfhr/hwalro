import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { FileText } from 'lucide-react';
import { useAuth } from '../../auth/context/AuthContext';
import { reportApi } from '../api/reportApi';
import type {
  ReportListItem,
  ReportListResponse,
  ReportListStatusFilter,
  ReportStatus,
} from '../types/report';
import { getReportErrorMessage } from '../utils/getReportErrorMessage';
import { canOpenReport, canRetryAiReport, isAiReportGenerating } from '../utils/reportStatus';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  type BadgeTone,
} from '../../../components/ui';

type StatusFilter = ReportListStatusFilter;

const PAGE_SIZE = 5;
const STATUS_BADGE_TONES: Record<ReportStatus, BadgeTone> = {
  'AI 작성 중': 'warning',
  '생성 실패': 'danger',
  초안: 'neutral',
  '작성 중': 'primary',
  완료: 'success',
};

function formatUpdatedAt(value: string): string {
  const [date, time = ''] = value.split('T');
  return `${date.split('-').join('. ')}. ${time.slice(0, 5)}`;
}

function getErrorMessage(error: unknown): string {
  return getReportErrorMessage(error, '보고서 목록을 불러오지 못했습니다.');
}

function ReportListPage() {
  const { user } = useAuth();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<StatusFilter>('전체');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<ReportListResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [retryingId, setRetryingId] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ReportListItem | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const requestSequenceRef = useRef(0);
  const isPollingRef = useRef(false);
  const canViewAllReports =
    user?.roles.includes('SAFETY_REVIEWER') || user?.roles.includes('ADMIN') || false;

  const loadReports = useCallback(
    async (showLoading: boolean) => {
      const requestSequence = requestSequenceRef.current + 1;
      requestSequenceRef.current = requestSequence;
      if (showLoading) {
        setIsLoading(true);
        setError(null);
      }
      try {
        const response = await reportApi.list({
          query: query.trim() || undefined,
          status: status === '전체' ? undefined : status,
          page,
          size: PAGE_SIZE,
        });
        if (requestSequenceRef.current === requestSequence) setData(response);
      } catch (requestError) {
        if (showLoading && requestSequenceRef.current === requestSequence) {
          setError(getErrorMessage(requestError));
        }
      } finally {
        if (showLoading && requestSequenceRef.current === requestSequence) setIsLoading(false);
      }
    },
    [page, query, status],
  );

  useEffect(() => {
    void loadReports(true);
    return () => {
      requestSequenceRef.current += 1;
    };
  }, [loadReports]);

  const totalCount = data?.totalCount ?? 0;
  const pageCount = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
  const reports = data?.items ?? [];
  const hasGeneratingReport = reports.some((report) => isAiReportGenerating(report.status));

  useEffect(() => {
    if (!hasGeneratingReport) return;
    const timer = window.setInterval(() => {
      if (isPollingRef.current) return;
      isPollingRef.current = true;
      void loadReports(false).finally(() => {
        isPollingRef.current = false;
      });
    }, 3000);
    return () => window.clearInterval(timer);
  }, [hasGeneratingReport, loadReports]);

  function updateQuery(nextQuery: string) {
    setQuery(nextQuery);
    setPage(1);
  }

  function updateStatus(nextStatus: StatusFilter) {
    setStatus(nextStatus);
    setPage(1);
  }

  async function retryAiReport(reportId: number) {
    setRetryingId(reportId);
    setActionError(null);
    try {
      await reportApi.retryAiDraft(reportId);
      await loadReports(false);
    } catch (requestError) {
      setActionError(
        getReportErrorMessage(requestError, 'AI 보고서 생성을 재시도하지 못했습니다.'),
      );
    } finally {
      setRetryingId(null);
    }
  }

  function openDeleteModal(report: ReportListItem) {
    setDeleteError(null);
    setDeleteTarget(report);
  }

  function closeDeleteModal() {
    if (deletingId !== null) return;
    setDeleteTarget(null);
    setDeleteError(null);
  }

  async function deleteReport() {
    if (!deleteTarget) return;
    setDeletingId(deleteTarget.id);
    setDeleteError(null);
    setActionError(null);
    try {
      await reportApi.delete(deleteTarget.id);
      setDeleteTarget(null);
      if (reports.length === 1 && page > 1) {
        setPage((currentPage) => currentPage - 1);
      } else {
        await loadReports(false);
      }
    } catch (requestError) {
      setDeleteError(getReportErrorMessage(requestError, '보고서를 삭제하지 못했습니다.'));
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <main className="bg-background">
      <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
        <div className="border-b border-line pb-6">
          <PageHeader
            eyebrow="보고서"
            title="보고서 목록"
            description="시뮬레이션 결과를 바탕으로 작성된 안전 검토 보고서를 확인합니다."
          />
        </div>

        <Card className="mt-5 bg-surface-sunken shadow-neu-pressed" aria-label="보고서 검색">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
            <label htmlFor="report-search" className="sr-only">
              보고서 제목 검색
            </label>
            <Input
              id="report-search"
              type="search"
              value={query}
              onChange={(event) => updateQuery(event.target.value)}
              placeholder="보고서 제목 검색"
              className="min-w-0 flex-1"
            />
            <label htmlFor="report-status" className="sr-only">
              보고서 상태
            </label>
            <Select
              id="report-status"
              value={status}
              onChange={(event) => updateStatus(event.target.value as StatusFilter)}
              className="lg:w-44"
            >
              <option value="전체">전체</option>
              <option value="AI 작성 중">AI 작성 중</option>
              <option value="생성 실패">생성 실패</option>
              <option value="초안">초안</option>
              <option value="작성 중">작성 중</option>
              <option value="완료">완료</option>
            </Select>
          </div>
        </Card>

        <Card
          className="mt-5 overflow-hidden bg-surface-raised shadow-neu-raised"
          padded={false}
          aria-label="보고서 목록"
        >
          {isLoading ? (
            <div className="px-5 py-5 sm:px-7">
              <div className="space-y-5">
                {Array.from({ length: 5 }, (_, index) => (
                  <div key={index} className="flex items-center justify-between gap-4">
                    <div className="min-w-0 flex-1 space-y-2">
                      <Skeleton className="h-4 w-2/3" />
                      <Skeleton className="h-3 w-1/3" />
                    </div>
                    <Skeleton className="h-6 w-20 rounded-full" />
                  </div>
                ))}
              </div>
            </div>
          ) : error ? (
            <div className="flex min-h-64 items-center justify-center px-6">
              <ErrorState message={error} className="w-full" />
            </div>
          ) : reports.length > 0 ? (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[540px] table-fixed border-collapse text-left">
                  <caption className="sr-only">보고서 목록</caption>
                  <colgroup>
                    <col className={canViewAllReports ? 'w-[44%]' : 'w-[54%]'} />
                    {canViewAllReports && <col className="w-[14%]" />}
                    <col className="w-[19%]" />
                    <col className="w-[13%]" />
                    <col className="w-[10%]" />
                  </colgroup>
                  <thead className="bg-surface-sunken text-xs font-bold tracking-wide text-text-muted">
                    <tr>
                      <th className="px-6 py-4">보고서 제목</th>
                      {canViewAllReports && <th className="px-4 py-4">작성자</th>}
                      <th className="px-4 py-4">최근 수정</th>
                      <th className="px-4 py-4">상태</th>
                      <th className="px-6 py-4 text-center">관리</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {reports.map((report) => (
                      <tr
                        key={report.id}
                        className="group transition-colors hover:bg-primary-soft/30"
                      >
                        <td className="px-6 py-4">
                          {canOpenReport(report.status) ? (
                            <Link
                              to={`/reports/${report.id}`}
                              className="block rounded outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
                            >
                              <span className="block text-sm font-bold text-ink group-hover:text-primary">
                                {report.title}
                              </span>
                              <span className="mt-1 block text-xs tabular-nums text-text-muted">
                                보고서 #{report.id}
                              </span>
                            </Link>
                          ) : (
                            <div aria-disabled="true">
                              <span className="block text-sm font-bold text-ink">
                                {report.title}
                              </span>
                              <span className="mt-1 block text-xs tabular-nums text-text-muted">
                                {isAiReportGenerating(report.status)
                                  ? 'AI가 보고서 초안을 작성하고 있습니다.'
                                  : `보고서 #${report.id}`}
                              </span>
                            </div>
                          )}
                        </td>
                        {canViewAllReports && (
                          <td className="px-4 py-4 text-sm font-bold text-text-strong">
                            {report.authorName ?? '-'}
                          </td>
                        )}
                        <td className="px-4 py-4 text-sm tabular-nums text-text-strong">
                          {formatUpdatedAt(report.updatedAt)}
                        </td>
                        <td className="px-4 py-4">
                          <div className="flex flex-col items-start gap-2">
                            <Badge tone={STATUS_BADGE_TONES[report.status]}>{report.status}</Badge>
                            {canRetryAiReport(report.status) && (
                              <button
                                type="button"
                                onClick={() => void retryAiReport(report.id)}
                                disabled={retryingId !== null}
                                className="rounded-md border border-danger/30 px-2 py-1 text-xs font-bold text-danger-strong outline-none transition hover:bg-danger-soft focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                {retryingId === report.id ? '재시도 중…' : '재시도'}
                              </button>
                            )}
                          </div>
                        </td>
                        <td className="px-6 py-4 text-center">
                          <div className="flex items-center justify-center opacity-0 transition-opacity duration-150 group-hover:opacity-100 focus-within:opacity-100">
                            <button
                              type="button"
                              onClick={() => openDeleteModal(report)}
                              disabled={deletingId !== null}
                              aria-label={`${report.title} 삭제`}
                              className="h-8 min-w-[52px] whitespace-nowrap rounded-lg border border-line bg-surface-raised px-2.5 text-xs font-bold text-text-muted shadow-neu-raised transition-[border-color,background-color,color,box-shadow] hover:border-danger/40 hover:bg-danger-soft hover:text-danger-strong active:shadow-neu-pressed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              삭제
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="flex flex-col items-center justify-between gap-3 border-t border-line px-5 py-3 sm:flex-row">
                <p className="text-sm tabular-nums text-text-muted">
                  총 {totalCount.toLocaleString()}건
                </p>
                <Pagination
                  page={page}
                  pageCount={pageCount}
                  onPageChange={setPage}
                  ariaLabel="보고서 목록 페이지"
                />
              </div>
              {actionError && (
                <div
                  role="alert"
                  className="border-t border-danger/25 bg-danger-soft px-5 py-3 text-sm text-danger-strong"
                >
                  {actionError}
                </div>
              )}
            </>
          ) : (
            <div className="flex min-h-64 items-center justify-center px-6">
              <EmptyState
                icon={FileText}
                title="조건에 맞는 보고서가 없습니다."
                description="검색어나 상태 필터를 변경한 뒤 다시 확인해보세요."
              />
            </div>
          )}
        </Card>
      </div>

      <Modal
        open={deleteTarget !== null}
        onClose={closeDeleteModal}
        title="보고서 삭제"
        description="삭제한 보고서는 복구할 수 없습니다."
        size="sm"
        footer={
          <>
            <Button
              type="button"
              variant="secondary"
              onClick={closeDeleteModal}
              disabled={deletingId !== null}
            >
              취소
            </Button>
            <Button
              type="button"
              variant="danger"
              onClick={() => void deleteReport()}
              isLoading={deletingId !== null}
            >
              삭제
            </Button>
          </>
        }
      >
        <p className="text-sm leading-6 text-text-strong">
          <span className="font-bold text-ink">{deleteTarget?.title}</span> 보고서를
          삭제하시겠습니까?
        </p>
        {deleteError && (
          <p
            role="alert"
            className="mt-3 rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger-strong"
          >
            {deleteError}
          </p>
        )}
      </Modal>
    </main>
  );
}

export default ReportListPage;
