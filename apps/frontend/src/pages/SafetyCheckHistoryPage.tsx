import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ChevronRight, ClipboardList, Trash2 } from 'lucide-react';
import { safetyCheckApi } from '../features/safetyChecks/api/safetyCheckApi';
import type {
  ChecklistTemplate,
  InspectionArea,
  InspectionHistory,
  InspectionStatus,
} from '../features/safetyChecks/types';
import {
  formatInspectionDate,
  getInspectionSummary,
  getSafetyCheckError,
} from '../features/safetyChecks/utils';
import { useAuth } from '../features/auth/context/AuthContext';
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Pagination,
  Skeleton,
} from '../components/ui';
import type { BadgeTone } from '../components/ui';
import SafetyCheckHeader from './safetyChecks/SafetyCheckHeader';

const PAGE_SIZE = 5;

function SnapshotThumb({ inspectionId }: { inspectionId: number }) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    safetyCheckApi
      .getSnapshot(inspectionId)
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch(() => {
        // 스냅샷이 없는 점검은 썸네일 없이 표시한다.
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [inspectionId]);

  if (!url) return null;
  return (
    <img
      src={url}
      alt={`점검 #${inspectionId} 도면 스냅샷`}
      className="h-16 w-24 rounded-lg border border-line bg-white object-cover"
      loading="lazy"
    />
  );
}

function getSummaryTone(needsAttention: boolean, status: InspectionStatus): BadgeTone {
  if (needsAttention) return 'danger';
  if (status === 'COMPLETED') return 'success';
  return 'neutral';
}

function SafetyCheckHistoryPage() {
  const { areaId: areaIdParam } = useParams();
  const areaId = Number(areaIdParam);
  const navigate = useNavigate();
  const { user } = useAuth();
  const [area, setArea] = useState<InspectionArea | null>(null);
  const [template, setTemplate] = useState<ChecklistTemplate | null>(null);
  const [inspections, setInspections] = useState<InspectionHistory[]>([]);
  const [page, setPage] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [isCreating, setIsCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [inspectionToDelete, setInspectionToDelete] = useState<InspectionHistory | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const canManageTemplate =
    user?.roles.includes('ADMIN') || user?.roles.includes('SAFETY_REVIEWER');
  const canStartInspection = area?.active === true && template?.id != null;
  const pageCount = Math.max(1, Math.ceil(inspections.length / PAGE_SIZE));
  const visibleInspections = inspections.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  useEffect(() => {
    let active = true;
    if (!Number.isSafeInteger(areaId) || areaId < 1) {
      setError('올바르지 않은 점검 구역입니다.');
      setIsLoading(false);
      return;
    }
    void Promise.all([
      safetyCheckApi.getArea(areaId),
      safetyCheckApi.getHistory(areaId),
      safetyCheckApi.getChecklistTemplate(areaId),
    ])
      .then(([inspectionArea, history, checklistTemplate]) => {
        if (!active) return;
        setArea(inspectionArea);
        setInspections(history);
        setTemplate(checklistTemplate);
      })
      .catch((requestError: unknown) => {
        if (active) setError(getSafetyCheckError(requestError));
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [areaId]);

  useEffect(() => {
    if (!isLoading && !error && page > pageCount) {
      setPage(pageCount);
    }
  }, [error, isLoading, page, pageCount]);

  async function createInspection() {
    setIsCreating(true);
    setActionError(null);
    try {
      const inspection = await safetyCheckApi.createInspection(areaId);
      navigate(`/safety-checklists/inspections/${inspection.id}`);
    } catch (requestError) {
      setActionError(getSafetyCheckError(requestError));
      setIsCreating(false);
    }
  }

  function openDeleteConfirm(inspection: InspectionHistory) {
    setInspectionToDelete(inspection);
    setActionError(null);
  }

  async function confirmDeleteInspection() {
    if (!inspectionToDelete) return;

    setDeletingId(inspectionToDelete.id);
    setActionError(null);
    try {
      await safetyCheckApi.deleteInspection(inspectionToDelete.id);
      setInspections((current) => current.filter((item) => item.id !== inspectionToDelete.id));
      setInspectionToDelete(null);
    } catch (requestError) {
      setActionError(getSafetyCheckError(requestError));
      setInspectionToDelete(null);
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
      <SafetyCheckHeader
        eyebrow="점검 구역"
        title={area?.name ?? '점검 이력'}
        description="이 구역에서 수행한 체크리스트를 시간순으로 확인합니다."
        backTo="/safety-checklists"
        backLabel="점검 구역 목록"
        action={
          <div className="flex flex-wrap gap-3">
            {canManageTemplate && (
              <Button
                type="button"
                variant="secondary"
                size="lg"
                onClick={() => navigate(`/safety-checklists/areas/${areaId}/template`)}
                disabled={!area?.active}
                className="cursor-pointer"
              >
                점검 항목 관리
              </Button>
            )}
            <Button
              type="button"
              size="lg"
              onClick={() => void createInspection()}
              disabled={isCreating || !canStartInspection}
              isLoading={isCreating}
            >
              새 점검 시작
            </Button>
          </div>
        }
      />

      {actionError && <ErrorState message={actionError} className="mt-5" />}

      {!isLoading && area && !area.active && (
        <div className="mt-5 rounded-lg border border-line bg-warning-soft px-4 py-3 text-sm font-medium text-warning-strong">
          삭제된 점검 구역입니다. 기존 점검 이력만 조회할 수 있습니다.
        </div>
      )}

      {!isLoading && area?.active && template?.id == null && (
        <div className="mt-5 rounded-lg border border-line bg-warning-soft px-4 py-3 text-sm font-medium text-warning-strong">
          활성 체크리스트가 없어 새 점검을 시작할 수 없습니다. 점검 항목 관리에서 항목을 저장하세요.
        </div>
      )}

      <Card padded={false} className="mt-5 overflow-hidden">
        <div className="border-b border-line px-5 py-4 sm:px-7">
          <h2 className="text-xl font-bold text-ink">점검 이력</h2>
        </div>
        {isLoading ? (
          <div className="space-y-5 p-5 sm:p-7">
            {Array.from({ length: PAGE_SIZE }, (_, index) => (
              <div key={index} className="flex items-center justify-between gap-4">
                <div className="space-y-2">
                  <Skeleton className="h-4 w-44" />
                  <Skeleton className="h-3 w-24" />
                </div>
                <Skeleton className="h-5 w-24" />
              </div>
            ))}
          </div>
        ) : error ? (
          <div className="flex min-h-64 items-center justify-center px-6">
            <ErrorState message={error} className="w-full" />
          </div>
        ) : inspections.length === 0 ? (
          <div className="p-5 sm:p-7">
            <EmptyState
              icon={ClipboardList}
              title="아직 수행한 점검이 없습니다."
              description="새 점검을 시작하면 이곳에 이력이 쌓입니다."
            />
          </div>
        ) : (
          <>
            <div className="divide-y divide-line">
              {visibleInspections.map((inspection) => {
                const needsAttention =
                  inspection.failCount > 0 || inspection.reviewRequiredCount > 0;
                const inspectorName =
                  inspection.inspectorId === user?.id
                    ? user.name
                    : `점검자 #${inspection.inspectorId}`;
                const canDelete =
                  inspection.status === 'DRAFT' &&
                  (inspection.inspectorId === user?.id || user?.roles.includes('ADMIN'));
                const statusTone = inspection.status === 'COMPLETED' ? 'success' : 'warning';
                const summaryTone = getSummaryTone(needsAttention, inspection.status);
                return (
                  <div key={inspection.id} className="group relative">
                    <button
                      type="button"
                      onClick={() => navigate(`/safety-checklists/inspections/${inspection.id}`)}
                      className="grid w-full cursor-pointer gap-4 px-5 py-4 text-left transition hover:bg-primary-soft/30 sm:px-7 md:grid-cols-2 md:items-center xl:grid-cols-[minmax(0,1.35fr)_minmax(10rem,0.9fr)_minmax(14rem,1fr)_18rem]"
                    >
                      <div>
                        <p className="font-bold tabular-nums text-ink">
                          {formatInspectionDate(inspection.createdAt)}
                        </p>
                        <p className="mt-1 text-xs tabular-nums text-text-muted">
                          점검 #{inspection.id}
                        </p>
                        {inspection.hasSnapshot && (
                          <div className="mt-2 hidden xl:block">
                            <SnapshotThumb inspectionId={inspection.id} />
                          </div>
                        )}
                      </div>
                      <div>
                        <p className="text-xs text-text-muted">점검 담당자</p>
                        <p className="mt-1 text-sm font-bold tabular-nums text-text-strong">
                          {inspectorName}
                        </p>
                      </div>
                      <div>
                        <p className="text-xs text-text-muted">진행률</p>
                        <div className="mt-2 flex items-center gap-3">
                          <div className="h-1.5 w-24 overflow-hidden rounded-full bg-surface">
                            <div
                              className="h-full rounded-full bg-primary"
                              style={{
                                width: `${inspection.totalItemCount === 0 ? 0 : (inspection.completedItemCount / inspection.totalItemCount) * 100}%`,
                              }}
                            />
                          </div>
                          <span className="text-xs font-bold tabular-nums text-text-strong">
                            {inspection.completedItemCount}/{inspection.totalItemCount}
                          </span>
                        </div>
                      </div>
                      <div
                        className={`flex flex-wrap items-center gap-2 md:justify-end ${canDelete ? 'pr-10' : ''}`}
                      >
                        <Badge tone={statusTone}>
                          {inspection.status === 'COMPLETED' ? '점검 완료' : '작성 중'}
                        </Badge>
                        <Badge tone={summaryTone} className="tabular-nums">
                          {getInspectionSummary(inspection)}
                        </Badge>
                        <ChevronRight aria-hidden="true" className="h-4 w-4 text-text-muted" />
                      </div>
                    </button>
                    {canDelete && (
                      <button
                        type="button"
                        onClick={() => openDeleteConfirm(inspection)}
                        disabled={deletingId === inspection.id}
                        aria-label={`점검 #${inspection.id} 삭제`}
                        title="작성 중 점검 삭제"
                        className="absolute right-4 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-lg text-text-muted transition hover:bg-danger-soft hover:text-danger disabled:opacity-40"
                      >
                        <Trash2 aria-hidden="true" className="h-4 w-4" />
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
            <div className="flex flex-col items-center justify-between gap-3 border-t border-line px-5 py-3 sm:flex-row">
              <p className="text-sm tabular-nums text-text-muted">
                총 {inspections.length.toLocaleString()}건
              </p>
              <Pagination
                page={page}
                pageCount={pageCount}
                onPageChange={setPage}
                ariaLabel="점검 이력 목록 페이지"
              />
            </div>
          </>
        )}
      </Card>

      {inspectionToDelete && (
        <ConfirmDialog
          open
          title="점검 삭제"
          description={`점검 #${inspectionToDelete.id}을 삭제하시겠습니까?`}
          isLoading={deletingId !== null}
          onCancel={() => setInspectionToDelete(null)}
          onConfirm={() => void confirmDeleteInspection()}
        >
          <p className="text-sm text-text-muted">삭제한 작성 중 점검은 복구할 수 없습니다.</p>
        </ConfirmDialog>
      )}
    </div>
  );
}

export default SafetyCheckHistoryPage;
