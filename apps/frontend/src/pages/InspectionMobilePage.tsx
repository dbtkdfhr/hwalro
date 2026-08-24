import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { Check, ClipboardList, X } from 'lucide-react';
import { safetyCheckApi } from '../features/safetyChecks/api/safetyCheckApi';
import type {
  InspectionDetail,
  InspectionItem,
  InspectionResult,
  InspectionStatus,
} from '../features/safetyChecks/types';
import { getSafetyCheckError, RESULT_LABELS } from '../features/safetyChecks/utils';
import { Badge, Button, EmptyState, ErrorState, Skeleton } from '../components/ui';

const RESULT_ORDER: InspectionResult[] = ['PENDING', 'PASS', 'REVIEW_REQUIRED', 'FAIL'];

const RESULT_CHIP_STYLES: Record<InspectionResult, string> = {
  PENDING: 'border-line bg-surface-sunken text-text-muted',
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
          <Skeleton className="h-24 w-full rounded-2xl" />
          <Skeleton className="h-32 w-full rounded-2xl" />
          <Skeleton className="h-32 w-full rounded-2xl" />
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
      <header className="sticky top-0 z-10 border-b border-line bg-surface-raised/95 px-4 py-3 shadow-neu-raised backdrop-blur">
        <div className="flex items-center gap-3">
          <div className="min-w-0">
            <p className="truncate text-base font-black text-ink">{areaName}</p>
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
          className="mt-3 h-2 overflow-hidden rounded-full bg-surface-sunken shadow-neu-pressed"
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
          className={`mx-4 mt-3 rounded-lg border border-line px-4 py-3 text-sm font-bold shadow-neu-pressed ${saveError ? 'bg-danger-soft text-danger-strong' : 'bg-success-soft text-success-strong'}`}
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

        <ol className="mt-4 space-y-3">
          {items.map((item) => (
            <li
              key={item.id}
              className={`rounded-2xl border border-line p-4 shadow-neu-raised ${item.result === 'FAIL' ? 'bg-danger-soft/40' : 'bg-surface-raised'}`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-black text-ink">
                    <span className="mr-2 text-text-muted tabular-nums">{item.displayOrder}.</span>
                    {item.title}
                  </p>
                  <p className="mt-1 text-xs leading-5 text-text-muted">
                    {item.criterion ?? '별도 판정 기준 없음'}
                  </p>
                </div>
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
              </div>
              <input
                type="text"
                value={item.comment ?? ''}
                onChange={(event) => updateComment(item, event.target.value)}
                readOnly={!canEdit || isSaving}
                aria-label={`${item.title} 확인 내용`}
                placeholder="확인 내용 또는 필요한 조치"
                className="mt-3 w-full rounded-lg border border-line bg-surface-sunken px-3 py-2 text-xs text-text-strong shadow-neu-pressed outline-none placeholder:text-text-muted focus:border-primary focus:ring-1 focus:ring-primary read-only:bg-surface-sunken/70"
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
        <div className="fixed inset-x-0 bottom-0 z-10 border-t border-line bg-surface-raised/95 px-4 py-3 shadow-neu-floating backdrop-blur">
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
