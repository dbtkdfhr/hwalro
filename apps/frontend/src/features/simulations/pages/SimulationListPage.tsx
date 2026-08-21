import { useQuery } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { LayoutGrid } from 'lucide-react';
import { simulationApi } from '../api/simulationApi';
import { SimulationStatusDialog } from '../components/SimulationStatusDialog';
import { STATUS_LABELS, STATUS_STYLES } from '../constants/simulationStatus';
import type { SimulationExecution, SimulationOverview } from '../types';
import { getSimulationErrorMessage } from '../utils/getSimulationErrorMessage';
import {
  getSimulationListAction,
  readStatusDialogSimulationId,
} from '../utils/simulationListAction';
import {
  buttonClassName,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Input,
  PageHeader,
  Pagination,
} from '../../../components/ui';
import { useDebounce } from '../../../hooks/useDebounce';

const PAGE_SIZE = 5;

function formatDateTime(value: string | null): string {
  if (!value) return '-';
  const [date, time = ''] = value.split('T');
  return `${date.split('-').join('. ')}. ${time.slice(0, 5)}`;
}

function resultLabel(simulation: SimulationOverview): string {
  if (simulation.terminationReason === 'ALL_EVACUATED') return '전원 대피';
  if (simulation.terminationReason === 'STALLED') return '정체 종료';
  if (simulation.terminationReason === 'MAX_DURATION') return '최대시간 도달';
  return '-';
}

function SimulationListPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [searchQuery, setSearchQuery] = useState('');
  const debouncedSearchQuery = useDebounce(searchQuery, 300);
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [pendingCancellation, setPendingCancellation] = useState<SimulationOverview | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [pendingDeletion, setPendingDeletion] = useState<SimulationOverview | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [selectedSimulation, setSelectedSimulation] = useState<SimulationOverview | null>(null);
  const [selectedExecution, setSelectedExecution] = useState<SimulationExecution | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [isRetrying, setIsRetrying] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const detailRequestSequenceRef = useRef(0);
  const statusDialogSimulationId = readStatusDialogSimulationId(location.state);
  const query = useQuery({
    queryKey: ['simulations', 'overview', page, debouncedSearchQuery],
    queryFn: () => simulationApi.listOverview(page, PAGE_SIZE, debouncedSearchQuery),
  });
  const items = query.data?.items ?? [];
  const totalPages = Math.max(1, Math.ceil((query.data?.totalCount ?? 0) / PAGE_SIZE));
  const hasRunning = items.some(
    (simulation) => simulation.status === 'REQUESTED' || simulation.status === 'RUNNING',
  );

  useEffect(() => {
    if (!hasRunning) return;
    const timer = window.setInterval(() => void query.refetch(), 3000);
    return () => window.clearInterval(timer);
  }, [hasRunning, query.refetch]);

  const cancelSimulation = async () => {
    if (!pendingCancellation) return;
    const simulationId = pendingCancellation.id;
    setCancellingId(simulationId);
    setCancelError(null);
    try {
      await simulationApi.cancel(simulationId);
      setPendingCancellation(null);
      await query.refetch();
    } catch (error) {
      setCancelError(getSimulationErrorMessage(error));
    } finally {
      setCancellingId(null);
    }
  };

  const deleteSimulation = async () => {
    if (!pendingDeletion) return;
    const simulationId = pendingDeletion.id;
    setDeletingId(simulationId);
    setDeleteError(null);
    try {
      await simulationApi.delete(simulationId);
      setPendingDeletion(null);
      if (items.length === 1 && page > 1) {
        setPage((p) => Math.max(1, p - 1));
      }
      await query.refetch();
    } catch (error) {
      setDeleteError(getSimulationErrorMessage(error));
    } finally {
      setDeletingId(null);
    }
  };

  const closeStatusDialog = () => {
    detailRequestSequenceRef.current += 1;
    setSelectedSimulation(null);
    setSelectedExecution(null);
    setIsDetailLoading(false);
    setDialogError(null);
  };

  const openStatusDialog = useCallback(async (simulation: SimulationOverview) => {
    const action = getSimulationListAction(simulation);
    if (action.type !== 'show-failure' && action.type !== 'show-cancelled') return;

    setSelectedSimulation(simulation);
    setSelectedExecution(null);
    setDialogError(null);
    if (action.type === 'show-cancelled') {
      setIsDetailLoading(false);
      return;
    }

    const requestSequence = detailRequestSequenceRef.current + 1;
    detailRequestSequenceRef.current = requestSequence;
    setIsDetailLoading(true);
    try {
      const execution = await simulationApi.getExecution(simulation.id);
      if (detailRequestSequenceRef.current === requestSequence) setSelectedExecution(execution);
    } catch (error) {
      if (detailRequestSequenceRef.current === requestSequence) {
        setDialogError(getSimulationErrorMessage(error));
      }
    } finally {
      if (detailRequestSequenceRef.current === requestSequence) setIsDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (statusDialogSimulationId === null) return;
    let active = true;
    setActionError(null);

    void simulationApi
      .getOverview(statusDialogSimulationId)
      .then((simulation) => {
        if (active) return openStatusDialog(simulation);
      })
      .catch((error) => {
        if (active) setActionError(getSimulationErrorMessage(error));
      })
      .finally(() => {
        if (active) {
          navigate(`${location.pathname}${location.search}`, { replace: true, state: null });
        }
      });

    return () => {
      active = false;
    };
  }, [location.pathname, location.search, navigate, openStatusDialog, statusDialogSimulationId]);

  const retrySelectedSimulation = async () => {
    if (!selectedSimulation) return;
    setIsRetrying(true);
    setDialogError(null);
    try {
      await simulationApi.execute(selectedSimulation.id);
      closeStatusDialog();
      await query.refetch();
    } catch (error) {
      setDialogError(getSimulationErrorMessage(error));
    } finally {
      setIsRetrying(false);
    }
  };

  const renderSimulationLink = (simulation: SimulationOverview) => {
    const action = getSimulationListAction(simulation);
    const content = (
      <>
        <span className="block max-w-64 truncate text-sm font-bold text-ink group-hover:text-primary">
          {simulation.title || simulation.layoutTitle}
        </span>
        <span className="mt-1 block text-xs tabular-nums text-text-muted">
          도면: {simulation.layoutTitle} · 버전 {simulation.layoutVersionNumber}
        </span>
      </>
    );

    if (action.type === 'navigate') {
      return (
        <Link
          to={action.to}
          className="group block rounded outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
        >
          {content}
        </Link>
      );
    }
    if (action.type === 'show-failure' || action.type === 'show-cancelled') {
      return (
        <button
          type="button"
          onClick={() => void openStatusDialog(simulation)}
          className="group block w-full rounded text-left outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
        >
          {content}
        </button>
      );
    }
    return (
      <div aria-disabled="true" className="cursor-not-allowed opacity-60">
        {content}
      </div>
    );
  };

  return (
    <main className="bg-background">
      <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
        <div className="border-b border-line pb-6">
          <PageHeader
            eyebrow="시뮬레이션"
            title="시뮬레이션 목록"
            description="실행 중인 작업과 이전 결과를 확인하고 배치 또는 결과 화면으로 다시 이동할 수 있습니다."
          />
        </div>

        <Card className="mt-5" aria-label="시뮬레이션 검색">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
            <label htmlFor="simulation-search" className="sr-only">
              시뮬레이션 검색
            </label>
            <Input
              id="simulation-search"
              type="search"
              value={searchQuery}
              onChange={(event) => {
                setSearchQuery(event.target.value);
                setPage(1);
              }}
              placeholder="시뮬레이션 검색"
              className="min-w-0 flex-1"
            />
          </div>
        </Card>

        <Card className="mt-5 overflow-hidden" padded={false} aria-label="시뮬레이션 목록">
          {query.isPending ? (
            <div className="flex min-h-64 items-center justify-center px-6 text-center text-sm text-text-muted">
              시뮬레이션을 불러오는 중입니다.
            </div>
          ) : query.isError && items.length === 0 ? (
            <div className="flex min-h-64 items-center justify-center px-6">
              <ErrorState
                message={getSimulationErrorMessage(query.error)}
                onRetry={() => void query.refetch()}
                className="w-full"
              />
            </div>
          ) : items.length === 0 ? (
            debouncedSearchQuery.trim() ? (
              <EmptyState
                icon={LayoutGrid}
                title="검색 결과가 없습니다."
                description="다른 검색어로 시뮬레이션을 검색해 보세요."
              />
            ) : (
              <EmptyState
                icon={LayoutGrid}
                title="생성된 시뮬레이션이 없습니다."
                description="도면 목록에서 배치를 작성한 뒤 시뮬레이션을 시작할 수 있습니다."
                action={
                  <Link
                    to="/drawings"
                    className={buttonClassName({ variant: 'primary', size: 'md' })}
                  >
                    도면 목록으로 이동
                  </Link>
                }
              />
            )
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[1040px] table-fixed border-collapse text-left">
                  <caption className="sr-only">시뮬레이션 실행 및 배치 목록</caption>
                  <colgroup>
                    <col className="w-[31%]" />
                    <col className="w-[13%]" />
                    <col className="w-[11%]" />
                    <col className="w-[14%]" />
                    <col className="w-[18%]" />
                    <col className="w-[13%]" />
                  </colgroup>
                  <thead className="bg-surface text-xs font-bold tracking-wide text-text-muted">
                    <tr>
                      <th className="px-6 py-4">시뮬레이션</th>
                      <th className="px-4 py-4">상태</th>
                      <th className="px-4 py-4">인원</th>
                      <th className="px-4 py-4">결과</th>
                      <th className="whitespace-nowrap px-4 py-4">생성일</th>
                      <th className="px-6 py-4 text-center">관리</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {items.map((simulation) => (
                      <tr
                        key={simulation.id}
                        className="group transition-colors hover:bg-primary-soft/30"
                      >
                        <td className="px-6 py-4">{renderSimulationLink(simulation)}</td>
                        <td className="px-4 py-4">
                          <span
                            className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ${STATUS_STYLES[simulation.status]}`}
                          >
                            {STATUS_LABELS[simulation.status]}
                          </span>
                        </td>
                        <td className="px-4 py-4 text-sm tabular-nums text-text-strong">
                          {simulation.totalPeople.toLocaleString()}명
                        </td>
                        <td className="px-4 py-4 text-sm text-text-strong">
                          {resultLabel(simulation)}
                        </td>
                        <td className="whitespace-nowrap px-4 py-4 text-sm tabular-nums text-text-strong">
                          {formatDateTime(simulation.startedAt ?? simulation.createdAt)}
                        </td>
                        <td className="px-6 py-4 text-center">
                          <div className="flex items-center justify-center opacity-0 transition-opacity duration-150 group-hover:opacity-100 focus-within:opacity-100">
                            {simulation.status === 'REQUESTED' ||
                            simulation.status === 'RUNNING' ? (
                              <button
                                type="button"
                                onClick={() => {
                                  setPendingCancellation(simulation);
                                  setCancelError(null);
                                }}
                                disabled={cancellingId !== null || deletingId !== null}
                                className="h-8 min-w-[73px] whitespace-nowrap rounded-lg border border-danger/25 bg-white px-3 text-xs font-bold text-danger-strong transition hover:bg-danger-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                {cancellingId === simulation.id ? '취소 중…' : '실행 취소'}
                              </button>
                            ) : (
                              <button
                                type="button"
                                onClick={() => {
                                  setPendingDeletion(simulation);
                                  setDeleteError(null);
                                }}
                                disabled={cancellingId !== null || deletingId !== null}
                                className="h-8 min-w-[52px] whitespace-nowrap rounded-lg border border-line bg-white px-2.5 text-xs font-bold text-text-muted transition hover:border-danger/40 hover:bg-danger-soft hover:text-danger-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                {deletingId === simulation.id ? '삭제 중…' : '삭제'}
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="flex flex-col items-center justify-between gap-3 border-t border-line px-5 py-3 sm:flex-row">
                <p className="text-sm tabular-nums text-text-muted">
                  총 {(query.data?.totalCount ?? 0).toLocaleString()}건
                </p>
                <Pagination
                  page={page}
                  pageCount={totalPages}
                  onPageChange={setPage}
                  disabled={query.isFetching}
                  ariaLabel="시뮬레이션 목록 페이지"
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
              {query.isError && (
                <div
                  role="alert"
                  className="flex items-center justify-between gap-4 border-t border-danger/25 bg-danger-soft px-5 py-3 text-sm text-danger-strong"
                >
                  <span>{getSimulationErrorMessage(query.error)}</span>
                  <button
                    type="button"
                    onClick={() => void query.refetch()}
                    disabled={query.isFetching}
                    className="shrink-0 rounded-lg border border-danger/40 px-3 py-1.5 font-bold outline-none transition hover:bg-danger-strong/10 focus-visible:ring-2 focus-visible:ring-focus-ring disabled:opacity-50"
                  >
                    다시 시도
                  </button>
                </div>
              )}
            </>
          )}
        </Card>
      </div>
      <SimulationStatusDialog
        simulation={selectedSimulation}
        execution={selectedExecution}
        isLoading={isDetailLoading}
        isRetrying={isRetrying}
        error={dialogError}
        onClose={closeStatusDialog}
        onRetry={() => void retrySelectedSimulation()}
      />
      <ConfirmDialog
        open={pendingCancellation !== null}
        title="시뮬레이션 실행 취소"
        description={
          pendingCancellation
            ? `${pendingCancellation.title || pendingCancellation.layoutTitle} · 시뮬레이션 #${pendingCancellation.id}`
            : undefined
        }
        confirmLabel="실행 취소"
        cancelLabel="돌아가기"
        isLoading={cancellingId !== null}
        onCancel={() => {
          setPendingCancellation(null);
          setCancelError(null);
        }}
        onConfirm={() => void cancelSimulation()}
      >
        <div className="space-y-2 text-sm leading-6">
          <p className="font-bold text-text-strong">진행 중인 시뮬레이션을 취소하시겠습니까?</p>
          <p className="text-text-muted">
            취소된 시뮬레이션은 동일한 설정으로 다시 실행할 수 있습니다.
          </p>
        </div>
        {cancelError && (
          <p
            role="alert"
            className="mt-4 rounded-lg border border-danger/25 bg-danger-soft px-3 py-2 text-sm text-danger-strong"
          >
            {cancelError}
          </p>
        )}
      </ConfirmDialog>
      <ConfirmDialog
        open={pendingDeletion !== null}
        title="시뮬레이션 삭제"
        description={
          pendingDeletion
            ? `${pendingDeletion.title || pendingDeletion.layoutTitle} · 시뮬레이션 #${pendingDeletion.id}`
            : undefined
        }
        confirmLabel="삭제"
        cancelLabel="돌아가기"
        isLoading={deletingId !== null}
        onCancel={() => {
          setPendingDeletion(null);
          setDeleteError(null);
        }}
        onConfirm={() => void deleteSimulation()}
      >
        <div className="space-y-2 text-sm leading-6">
          <p className="font-bold text-text-strong">시뮬레이션을 삭제하시겠습니까?</p>
          <p className="text-text-muted">
            개선안, 위험 예상 항목, 보고서에 연결된 시뮬레이션은 삭제할 수 없으며, 삭제된
            시뮬레이션은 복구할 수 없습니다.
          </p>
        </div>
        {deleteError && (
          <p
            role="alert"
            className="mt-4 rounded-lg border border-danger/25 bg-danger-soft px-3 py-2 text-sm text-danger-strong"
          >
            {deleteError}
          </p>
        )}
      </ConfirmDialog>
    </main>
  );
}

export default SimulationListPage;
