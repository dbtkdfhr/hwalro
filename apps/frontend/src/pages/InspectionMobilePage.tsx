import { useEffect, useMemo, useState } from 'react';
import type { MouseEvent as ReactMouseEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { Check, ClipboardList, MapPin, X } from 'lucide-react';
import { safetyCheckApi } from '../features/safetyChecks/api/safetyCheckApi';
import {
  fetchLayoutDrawingContext,
  renderDrawingSnapshot,
} from '../features/safetyChecks/utils/drawingSnapshot';
import type {
  InspectionDetail,
  InspectionItem,
  InspectionResult,
  InspectionStatus,
} from '../features/safetyChecks/types';
import {
  getMarkerBadgeClass,
  getSafetyCheckError,
  RESULT_LABELS,
} from '../features/safetyChecks/utils';
import { Badge, Button, EmptyState, ErrorState, Skeleton } from '../components/ui';

const RESULT_ORDER: InspectionResult[] = ['PENDING', 'PASS', 'REVIEW_REQUIRED', 'FAIL'];

const RESULT_CHIP_STYLES: Record<InspectionResult, string> = {
  PENDING: 'border-line bg-surface text-text-muted',
  PASS: 'border-success-strong/30 bg-success-soft text-success-strong',
  REVIEW_REQUIRED: 'border-warning-strong/30 bg-warning-soft text-warning-strong',
  FAIL: 'border-danger-strong/30 bg-danger-soft text-danger-strong',
};

function nextResult(result: InspectionResult): InspectionResult {
  const index = RESULT_ORDER.indexOf(result);
  return RESULT_ORDER[(index + 1) % RESULT_ORDER.length];
}

function getResultIcon(result: InspectionResult) {
  if (result === 'PASS') {
    return <Check aria-hidden="true" className="h-4 w-4" />;
  }
  if (result === 'FAIL') {
    return <X aria-hidden="true" className="h-4 w-4" />;
  }
  return null;
}

function InspectionMobilePage() {
  const { areaId: areaIdParam } = useParams();
  const areaId = Number(areaIdParam);
  const navigate = useNavigate();
  const [areaName, setAreaName] = useState('');
  const [inspection, setInspection] = useState<InspectionDetail | null>(null);
  const [items, setItems] = useState<InspectionItem[]>([]);
  const [comment, setComment] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [snapshotUrl, setSnapshotUrl] = useState<string | null>(null);
  const [snapshotState, setSnapshotState] = useState<'idle' | 'loading' | 'ready' | 'error'>(
    'idle',
  );
  const [pinItemId, setPinItemId] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      if (!Number.isSafeInteger(areaId) || areaId < 1) {
        setError('올바르지 않은 점검 구역입니다.');
        setIsLoading(false);
        return;
      }
      try {
        const [area, detail] = await Promise.all([
          safetyCheckApi.getArea(areaId),
          safetyCheckApi.getOrCreateCurrentInspection(areaId),
        ]);
        if (!active) return;
        setAreaName(area.name);
        setInspection(detail);
        setItems(detail.items);
        setComment(detail.comment ?? '');
      } catch (requestError) {
        if (active) setError(getSafetyCheckError(requestError));
      } finally {
        if (active) setIsLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [areaId, loadAttempt]);

  const counts = useMemo(
    () => ({
      completed: items.filter((item) => item.result !== 'PENDING').length,
      fail: items.filter((item) => item.result === 'FAIL').length,
      review: items.filter((item) => item.result === 'REVIEW_REQUIRED').length,
    }),
    [items],
  );
  const canEdit = inspection?.status === 'DRAFT';
  const allAssessed = items.length > 0 && counts.completed === items.length;

  useEffect(() => {
    const layoutId = inspection?.layoutId ?? inspection?.areaLayoutId ?? null;
    if (!inspection || layoutId === null) {
      setSnapshotState('idle');
      return;
    }
    let active = true;
    let createdUrl: string | null = null;
    setSnapshotState('loading');
    void (async () => {
      try {
        const context = await fetchLayoutDrawingContext(layoutId);
        if (!active) return;
        let blob: Blob | null = null;
        try {
          blob = await safetyCheckApi.getSnapshot(inspection.id);
        } catch {
          blob = null;
        }
        if (!blob && inspection.status === 'DRAFT') {
          blob = await renderDrawingSnapshot(context.drawing);
          await safetyCheckApi.saveSnapshot(
            inspection.id,
            blob,
            context.layoutVersionId ?? undefined,
            layoutId,
          );
        }
        if (!blob || !active) return;
        createdUrl = URL.createObjectURL(blob);
        setSnapshotUrl(createdUrl);
        setSnapshotState('ready');
      } catch {
        if (active) setSnapshotState('error');
      }
    })();
    return () => {
      active = false;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [inspection]);

  function handleSnapshotClick(event: ReactMouseEvent<HTMLDivElement>) {
    if (!canEdit || pinItemId === null) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const markerX = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
    const markerY = Math.min(1, Math.max(0, (event.clientY - rect.top) / rect.height));
    setItems((current) =>
      current.map((entry) => (entry.id === pinItemId ? { ...entry, markerX, markerY } : entry)),
    );
    setNotice(null);
    setPinItemId(null);
  }

  function cycleResult(item: InspectionItem) {
    setItems((current) =>
      current.map((entry) =>
        entry.id === item.id ? { ...entry, result: nextResult(entry.result) } : entry,
      ),
    );
    setNotice(null);
  }

  function updateComment(item: InspectionItem, value: string) {
    setItems((current) =>
      current.map((entry) => (entry.id === item.id ? { ...entry, comment: value } : entry)),
    );
    setNotice(null);
  }

  async function save(status: InspectionStatus) {
    if (!inspection) return;
    setIsSaving(true);
    setError(null);
    setSaveError(null);
    setNotice(null);
    try {
      const updated = await safetyCheckApi.updateInspection(inspection.id, {
        status,
        comment: comment.trim() || null,
        items: items.map((item) => ({
          id: item.id,
          result: item.result,
          comment: item.comment?.trim() || null,
          markerX: item.markerX,
          markerY: item.markerY,
        })),
      });
      setInspection(updated);
      setItems(updated.items);
      setComment(updated.comment ?? '');
      setNotice(status === 'COMPLETED' ? '점검을 완료했습니다.' : '임시 저장했습니다.');
    } catch (requestError) {
      if (await handleSaveConflict(requestError)) return;
      setSaveError(
        getSafetyCheckError(
          requestError,
          '저장하지 못했습니다. 네트워크를 확인하고 다시 시도해 주세요.',
        ),
      );
    } finally {
      setIsSaving(false);
    }
  }

  async function handleSaveConflict(requestError: unknown): Promise<boolean> {
    if (!inspection || !(requestError instanceof AxiosError) || !requestError.response) {
      return false;
    }
    if (requestError.response.status === 404) {
      setError(
        '이 점검은 다른 곳에서 삭제되어 저장할 수 없습니다. 다시 시도하면 새 점검이 시작됩니다.',
      );
      return true;
    }
    if (requestError.response.status === 400) {
      try {
        const current = await safetyCheckApi.getInspection(inspection.id);
        if (current.status === 'COMPLETED') {
          setInspection(current);
          setItems(current.items);
          setComment(current.comment ?? '');
          setNotice('이 점검은 다른 세션에서 완료되어 더 이상 수정할 수 없습니다.');
          return true;
        }
      } catch {
        return false;
      }
    }
    return false;
  }

  if (isLoading) {
    return (
      <div className="mx-auto min-h-dvh w-full max-w-xl px-4 pb-10 pt-4">
        <Skeleton className="h-10 w-40" />
        <div className="mt-6 space-y-3">
          <Skeleton className="h-24 w-full rounded-xl" />
          <Skeleton className="h-32 w-full rounded-xl" />
          <Skeleton className="h-32 w-full rounded-xl" />
        </div>
      </div>
    );
  }

  if (error || !inspection) {
    return (
      <div className="mx-auto flex min-h-dvh w-full max-w-xl flex-col items-center justify-center px-6">
        <ErrorState message={error ?? '점검 정보를 찾을 수 없습니다.'} className="w-full" />
        <Button
          type="button"
          size="lg"
          className="mt-6 w-full"
          onClick={() => {
            setError(null);
            setIsLoading(true);
            setLoadAttempt((value) => value + 1);
          }}
        >
          다시 시도
        </Button>
        <Button
          type="button"
          variant="ghost"
          className="mt-3"
          onClick={() => navigate('/safety-checklists')}
        >
          점검 구역 목록으로
        </Button>
      </div>
    );
  }

  return (
    <div className="mx-auto min-h-dvh w-full max-w-xl bg-background pb-32">
      <header className="sticky top-0 z-10 border-b border-line bg-background/95 px-4 py-3 backdrop-blur">
        <div className="flex items-center gap-3">
          <div className="min-w-0">
            <p className="truncate text-base font-bold text-ink">{areaName}</p>
            <p className="text-xs font-bold text-text-muted">
              안전 점검 · {counts.completed}/{items.length} 항목 판정
            </p>
          </div>
        </div>
        <div
          role="progressbar"
          aria-label="점검 진행률"
          aria-valuemin={0}
          aria-valuemax={items.length}
          aria-valuenow={counts.completed}
          className="mt-3 h-2 overflow-hidden rounded-full bg-surface"
        >
          <div
            aria-hidden="true"
            className="h-full rounded-full bg-primary transition-all"
            style={{
              width: `${items.length === 0 ? 0 : (counts.completed / items.length) * 100}%`,
            }}
          />
        </div>
      </header>

      {(notice || saveError) && (
        <div
          role={saveError ? 'alert' : 'status'}
          className={`mx-4 mt-3 rounded-lg border border-line px-4 py-3 text-sm font-medium ${saveError ? 'bg-danger-soft text-danger-strong' : 'bg-success-soft text-success-strong'}`}
        >
          {saveError ?? notice}
        </div>
      )}

      <main className="px-4 pt-4">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            {counts.fail > 0 && (
              <Badge tone="danger" className="tabular-nums">
                부적합 {counts.fail}건
              </Badge>
            )}
            {counts.review > 0 && (
              <Badge tone="warning" className="tabular-nums">
                확인 필요 {counts.review}건
              </Badge>
            )}
          </div>
          {inspection.status === 'COMPLETED' && <Badge tone="success">완료된 점검</Badge>}
        </div>

        {snapshotState === 'loading' && (
          <p className="mt-3 text-xs font-medium text-text-muted">
            도면 이미지를 준비하고 있습니다...
          </p>
        )}
        {snapshotState === 'error' && (
          <p className="mt-3 rounded-lg border border-line bg-surface px-3 py-2 text-xs font-medium text-text-muted">
            도면 이미지를 준비하지 못했습니다.
          </p>
        )}
        {snapshotUrl && (
          <section className="mt-4" aria-label="도면 체크 현황">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-sm font-black text-ink">도면 체크 현황</h2>
              <span className="text-[11px] text-text-muted">
                {pinItemId !== null
                  ? '도면을 눌러 위치를 지정하세요.'
                  : canEdit
                    ? '항목의 “위치”를 누른 뒤 도면을 누르면 기록됩니다.'
                    : null}
              </span>
            </div>
            <div
              onClick={handleSnapshotClick}
              role={canEdit ? 'button' : undefined}
              tabIndex={canEdit && pinItemId !== null ? 0 : undefined}
              aria-label={
                pinItemId !== null ? '도면 위를 눌러 항목 위치를 지정' : '점검 도면 스냅샷'
              }
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  setPinItemId(null);
                }
              }}
              className={`relative mt-2 overflow-hidden rounded-xl border border-line bg-white ${
                pinItemId !== null && canEdit ? 'cursor-crosshair ring-2 ring-focus-ring' : ''
              }`}
            >
              <img
                src={snapshotUrl}
                alt={`${areaName} 점검 도면 스냅샷`}
                className="block w-full select-none"
                draggable={false}
              />
              {items
                .filter((item) => item.markerX !== null && item.markerY !== null)
                .map((item) => (
                  <span
                    key={`marker-${item.id}`}
                    style={{
                      left: `${(item.markerX as number) * 100}%`,
                      top: `${(item.markerY as number) * 100}%`,
                    }}
                    className={`pointer-events-none absolute flex h-6 w-6 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full text-[10px] font-black tabular-nums shadow-raised ${getMarkerBadgeClass(item.result)}`}
                    title={`${item.title} (${RESULT_LABELS[item.result]})`}
                  >
                    {item.displayOrder}
                  </span>
                ))}
            </div>
          </section>
        )}

        <ol className="mt-4 space-y-3">
          {items.map((item) => (
            <li
              key={item.id}
              className={`rounded-xl border border-line p-4 ${item.result === 'FAIL' ? 'bg-danger-soft/40' : 'bg-surface'}`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-bold text-ink">
                    <span className="mr-2 text-text-muted tabular-nums">{item.displayOrder}.</span>
                    {item.title}
                  </p>
                  <p className="mt-1 text-xs leading-5 text-text-muted">
                    {item.criterion ?? '별도 판정 기준 없음'}
                  </p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1.5">
                  <button
                    type="button"
                    onClick={() => canEdit && cycleResult(item)}
                    disabled={!canEdit || isSaving}
                    aria-label={
                      canEdit && !isSaving
                        ? `${item.title} 판정: ${RESULT_LABELS[item.result]} (누르면 다음 판정으로)`
                        : `${item.title} 판정: ${RESULT_LABELS[item.result]}`
                    }
                    className={`flex h-9 shrink-0 items-center gap-1 rounded-full border px-3 text-xs font-black outline-none transition-colors focus-visible:ring-2 focus-visible:ring-focus-ring disabled:opacity-70 ${RESULT_CHIP_STYLES[item.result]}`}
                  >
                    {getResultIcon(item.result)}
                    {RESULT_LABELS[item.result]}
                  </button>
                  {snapshotUrl && canEdit && (
                    <button
                      type="button"
                      onClick={() => setPinItemId(pinItemId === item.id ? null : item.id)}
                      aria-pressed={pinItemId === item.id}
                      title="도면 위 위치 지정"
                      className={`flex h-7 items-center gap-1 rounded-full border px-2.5 text-[11px] font-black outline-none transition-colors focus-visible:ring-2 focus-visible:ring-focus-ring ${
                        pinItemId === item.id
                          ? 'border-primary bg-primary-soft text-primary'
                          : 'border-line bg-surface text-text-strong'
                      }`}
                    >
                      <MapPin aria-hidden="true" className="h-3 w-3" />
                      위치
                    </button>
                  )}
                </div>
              </div>
              <input
                type="text"
                value={item.comment ?? ''}
                onChange={(event) => updateComment(item, event.target.value)}
                readOnly={!canEdit || isSaving}
                aria-label={`${item.title} 확인 내용`}
                placeholder="확인 내용 또는 필요한 조치"
                className="mt-3 w-full rounded-lg border border-line bg-surface/60 px-3 py-2 text-xs text-text-strong outline-none placeholder:text-text-muted focus:border-primary focus:ring-1 focus:ring-primary read-only:bg-surface/40"
              />
            </li>
          ))}
        </ol>
        {items.length === 0 && (
          <EmptyState
            icon={ClipboardList}
            title="등록된 점검 항목이 없습니다."
            description="관리자가 체크리스트 항목을 먼저 등록해야 이 구역을 점검할 수 있습니다."
          />
        )}
      </main>

      {canEdit && (
        <div className="fixed inset-x-0 bottom-0 z-10 border-t border-line bg-background/95 px-4 py-3 backdrop-blur">
          <div className="mx-auto flex max-w-xl gap-3">
            <Button
              type="button"
              variant="secondary"
              className="flex-1"
              isLoading={isSaving}
              onClick={() => void save('DRAFT')}
            >
              임시 저장
            </Button>
            <Button
              type="button"
              className="flex-1"
              isLoading={isSaving}
              disabled={!allAssessed}
              title={allAssessed ? undefined : '모든 항목을 판정한 뒤 완료할 수 있습니다.'}
              onClick={() => void save('COMPLETED')}
            >
              점검 완료
            </Button>
          </div>
        </div>
      )}

      {inspection.status === 'COMPLETED' && (
        <footer className="px-4 pb-6 text-center text-sm font-bold text-text-muted">
          이 점검은 완료되어 더 이상 수정할 수 없습니다.
        </footer>
      )}
    </div>
  );
}

export default InspectionMobilePage;
