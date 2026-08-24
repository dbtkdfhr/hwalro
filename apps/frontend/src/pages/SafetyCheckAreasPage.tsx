import { lazy, Suspense, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, QrCode } from 'lucide-react';
import { useAuth } from '../features/auth/context/AuthContext';
import { safetyCheckApi } from '../features/safetyChecks/api/safetyCheckApi';
import type { InspectionArea } from '../features/safetyChecks/types';
import { formatInspectionDate, getSafetyCheckError } from '../features/safetyChecks/utils';
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Field,
  Input,
  Pagination,
  Skeleton,
} from '../components/ui';
import SafetyCheckHeader from './safetyChecks/SafetyCheckHeader';

const AreaQrDialog = lazy(() => import('../features/safetyChecks/components/AreaQrDialog'));

const PAGE_SIZE = 6;

interface AreaEditor {
  id: number | null;
  name: string;
  description: string;
}

function SafetyCheckAreasPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [areas, setAreas] = useState<InspectionArea[]>([]);
  const [page, setPage] = useState(1);
  const [editor, setEditor] = useState<AreaEditor | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [areaToDelete, setAreaToDelete] = useState<InspectionArea | null>(null);
  const [qrArea, setQrArea] = useState<InspectionArea | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const canManage = user?.roles.includes('ADMIN') || user?.roles.includes('SAFETY_REVIEWER');
  const pageCount = Math.max(1, Math.ceil(areas.length / PAGE_SIZE));
  const visibleAreas = areas.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  useEffect(() => {
    let active = true;
    void safetyCheckApi
      .getAreas()
      .then((data) => {
        if (active) setAreas(data);
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
  }, []);

  useEffect(() => {
    if (!isLoading && !error && page > pageCount) {
      setPage(pageCount);
    }
  }, [error, isLoading, page, pageCount]);

  function openCreateEditor() {
    setEditor({ id: null, name: '', description: '' });
    setActionError(null);
  }

  function openEditEditor(area: InspectionArea) {
    setEditor({ id: area.id, name: area.name, description: area.description ?? '' });
    setActionError(null);
  }

  async function saveArea(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editor || !canManage) return;

    const name = editor.name.trim();
    if (!name) {
      setActionError('점검 구역 이름을 입력하세요.');
      return;
    }

    setIsSaving(true);
    setActionError(null);
    try {
      const body = { name, description: editor.description.trim() || null };
      if (editor.id === null) {
        const created = await safetyCheckApi.createArea(body);
        navigate(`/safety-checklists/areas/${created.id}/template`);
        return;
      }

      const updated = await safetyCheckApi.updateArea(editor.id, body);
      setAreas((current) => current.map((area) => (area.id === updated.id ? updated : area)));
      setEditor(null);
    } catch (requestError) {
      setActionError(getSafetyCheckError(requestError));
    } finally {
      setIsSaving(false);
    }
  }

  function openDeleteConfirm(area: InspectionArea) {
    setAreaToDelete(area);
    setActionError(null);
  }

  async function confirmDeleteArea() {
    if (!areaToDelete) return;

    setDeletingId(areaToDelete.id);
    setActionError(null);
    try {
      await safetyCheckApi.deleteArea(areaToDelete.id);
      setAreas((current) => current.filter((item) => item.id !== areaToDelete.id));
      if (editor?.id === areaToDelete.id) setEditor(null);
      setAreaToDelete(null);
    } catch (requestError) {
      setActionError(getSafetyCheckError(requestError));
      setAreaToDelete(null);
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <main className="bg-background">
      <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
        <div className="border-b border-line pb-6">
          <SafetyCheckHeader
            eyebrow="안전 운영"
            title="안전 점검·체크리스트"
            description="독립적으로 관리하는 점검 구역을 선택해 이전 점검 이력과 체크리스트 결과를 확인합니다."
          />
        </div>

        <section className="mt-5" aria-labelledby="inspection-area-title">
          <div className="flex items-end justify-between gap-4">
            <div>
              <h2 id="inspection-area-title" className="text-xl font-black text-ink">
                점검 구역
              </h2>
              <p className="mt-2 text-sm text-text-muted">
                등록된 구역 <span className="tabular-nums">{areas.length}</span>곳
              </p>
            </div>
            {canManage && (
              <Button type="button" size="lg" onClick={openCreateEditor}>
                점검 구역 추가
              </Button>
            )}
          </div>

          {actionError && <ErrorState message={actionError} className="mt-5" />}

          {editor && (
            <form
              onSubmit={(event) => void saveArea(event)}
              className="mt-5 rounded-xl border border-primary/25 bg-surface-raised p-5 shadow-neu-raised"
            >
              <div className="flex items-center justify-between gap-4">
                <h3 className="text-lg font-black text-ink">
                  {editor.id === null ? '점검 구역 추가' : '점검 구역 수정'}
                </h3>
                <Button type="button" variant="ghost" size="sm" onClick={() => setEditor(null)}>
                  취소
                </Button>
              </div>
              <div className="mt-4 grid gap-4 md:grid-cols-2">
                <Field label="구역 이름" htmlFor="area-name" required>
                  <Input
                    id="area-name"
                    type="text"
                    required
                    maxLength={200}
                    value={editor.name}
                    onChange={(event) => setEditor({ ...editor, name: event.target.value })}
                  />
                </Field>
                <Field label="설명" htmlFor="area-description">
                  <Input
                    id="area-description"
                    type="text"
                    maxLength={1000}
                    value={editor.description}
                    onChange={(event) =>
                      setEditor({
                        ...editor,
                        description: event.target.value,
                      })
                    }
                  />
                </Field>
              </div>
              <div className="mt-4 flex justify-end">
                <Button type="submit" isLoading={isSaving}>
                  {editor.id === null ? '추가 후 항목 설정' : '저장'}
                </Button>
              </div>
            </form>
          )}

          {isLoading ? (
            <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {Array.from({ length: PAGE_SIZE }, (_, index) => (
                <div
                  key={index}
                  className="rounded-xl border border-line bg-surface-raised p-5 shadow-neu-raised"
                >
                  <div className="flex items-start justify-between gap-4">
                    <Skeleton className="h-11 w-11 rounded-xl" />
                    <Skeleton className="h-5 w-16" />
                  </div>
                  <Skeleton className="mt-5 h-5 w-2/3" />
                  <Skeleton className="mt-2 h-4 w-full" />
                  <Skeleton className="mt-5 h-4 w-1/2" />
                </div>
              ))}
            </div>
          ) : error ? (
            <div className="mt-5 flex min-h-64 items-center justify-center">
              <ErrorState message={error} className="w-full" />
            </div>
          ) : areas.length === 0 ? (
            <div className="mt-5">
              <EmptyState
                icon={Building2}
                title="등록된 점검 구역이 없습니다."
                description="첫 점검 구역을 등록하면 점검 항목과 이력을 관리할 수 있습니다."
                action={
                  canManage ? (
                    <Button type="button" onClick={openCreateEditor}>
                      첫 점검 구역 추가
                    </Button>
                  ) : undefined
                }
              />
            </div>
          ) : (
            <>
              <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                {visibleAreas.map((area) => (
                  <Card
                    key={area.id}
                    interactive
                    padded={false}
                    className="group overflow-hidden bg-surface-raised shadow-neu-raised"
                  >
                    <button
                      type="button"
                      onClick={() => navigate(`/safety-checklists/areas/${area.id}`)}
                      className="w-full cursor-pointer p-5 text-left"
                    >
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary-soft text-primary">
                          <Building2 aria-hidden="true" className="h-6 w-6" />
                        </div>
                        <Badge tone="neutral" className="tabular-nums">
                          점검 {area.inspectionCount}회
                        </Badge>
                      </div>
                      <h3 className="mt-5 text-xl font-black text-ink group-hover:text-primary">
                        {area.name}
                      </h3>
                      <p className="mt-2 min-h-10 text-sm leading-5 text-text-muted">
                        {area.description ?? '구역 설명이 없습니다.'}
                      </p>
                      <div className="mt-5 flex items-center justify-between border-t border-line pt-4 text-xs text-text-muted">
                        <span>최근 점검</span>
                        <span className="font-bold tabular-nums text-text-strong">
                          {formatInspectionDate(area.lastInspectedAt)}
                        </span>
                      </div>
                    </button>
                    <div className="flex items-center justify-between border-t border-line px-5 py-3">
                      <button
                        type="button"
                        onClick={() => setQrArea(area)}
                        disabled={!area.hasActiveTemplate}
                        title={
                          area.hasActiveTemplate
                            ? '체크리스트 QR 배포'
                            : '체크리스트 항목을 먼저 등록해야 QR을 배포할 수 있습니다.'
                        }
                        className="flex items-center gap-1.5 rounded-lg border border-transparent bg-surface-raised px-3 py-2 text-sm font-bold text-text-strong shadow-neu-raised outline-none transition-[background-color,box-shadow] hover:bg-surface-sunken active:shadow-neu-pressed focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        <QrCode aria-hidden="true" className="h-4 w-4" />
                        QR 배포
                      </button>
                      {canManage && (
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => openEditEditor(area)}
                            className="rounded-lg border border-transparent bg-surface-raised px-3 py-2 text-sm font-bold text-text-strong shadow-neu-raised outline-none transition-[background-color,box-shadow] hover:bg-surface-sunken active:shadow-neu-pressed focus-visible:ring-2 focus-visible:ring-focus-ring"
                          >
                            수정
                          </button>
                          <button
                            type="button"
                            onClick={() => openDeleteConfirm(area)}
                            disabled={deletingId === area.id}
                            className="rounded-lg border border-transparent bg-surface-raised px-3 py-2 text-sm font-bold text-danger shadow-neu-raised outline-none transition-[background-color,box-shadow] hover:bg-danger-soft active:shadow-neu-pressed focus-visible:ring-2 focus-visible:ring-focus-ring disabled:opacity-50"
                          >
                            {deletingId === area.id ? '삭제 중...' : '삭제'}
                          </button>
                        </div>
                      )}
                    </div>
                  </Card>
                ))}
              </div>
              <div className="mt-4 flex flex-col items-center justify-between gap-3 border-t border-line px-5 py-3 sm:flex-row">
                <p className="text-sm tabular-nums text-text-muted">
                  총 {areas.length.toLocaleString()}개
                </p>
                <Pagination
                  page={page}
                  pageCount={pageCount}
                  onPageChange={setPage}
                  ariaLabel="안전 점검 구역 목록 페이지"
                />
              </div>
            </>
          )}
        </section>

        {areaToDelete && (
          <ConfirmDialog
            open
            title="점검 구역 삭제"
            description={`‘${areaToDelete.name}’ 점검 구역을 삭제하시겠습니까? 기존 점검 이력은 보존됩니다.`}
            isLoading={deletingId !== null}
            onCancel={() => setAreaToDelete(null)}
            onConfirm={() => void confirmDeleteArea()}
          >
            <p className="text-sm text-text-muted">삭제한 점검 구역은 복구할 수 없습니다.</p>
          </ConfirmDialog>
        )}

        {qrArea && (
          <Suspense fallback={null}>
            <AreaQrDialog area={qrArea} onClose={() => setQrArea(null)} />
          </Suspense>
        )}
      </div>
    </main>
  );
}

export default SafetyCheckAreasPage;
