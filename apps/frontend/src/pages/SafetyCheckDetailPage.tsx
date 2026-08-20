import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useParams } from 'react-router-dom';
import { Check, X } from 'lucide-react';
import { useAuth } from '../features/auth/context/AuthContext';
import { safetyCheckApi } from '../features/safetyChecks/api/safetyCheckApi';
import type {
  InspectionDetail,
  InspectionItem,
  InspectionResult,
  InspectionStatus,
} from '../features/safetyChecks/types';
import { getSafetyCheckError, RESULT_LABELS } from '../features/safetyChecks/utils';
import { Badge, Button, Card, ErrorState, Input, Skeleton, Textarea } from '../components/ui';
import SafetyCheckHeader from './safetyChecks/SafetyCheckHeader';

const RESULT_OPTIONS = Object.keys(RESULT_LABELS) as InspectionResult[];

const RESULT_SELECT_STYLES: Record<InspectionResult, string> = {
  PENDING: 'border-line bg-surface text-text-muted',
  REVIEW_REQUIRED: 'border-line bg-warning-soft text-warning-strong',
  PASS: 'border-line bg-success-soft text-success-strong',
  FAIL: 'border-line bg-danger-soft text-danger-strong',
};

function getResultIndicatorClass(result: InspectionResult): string {
  switch (result) {
    case 'PASS':
      return 'bg-success-soft text-success-strong';
    case 'FAIL':
      return 'bg-danger-soft text-danger-strong';
    default:
      return 'bg-soft-gray text-text-muted';
  }
}

function getResultIndicatorIcon(result: InspectionResult, displayOrder: number): ReactNode {
  switch (result) {
    case 'PASS':
      return <Check aria-hidden="true" className="h-4 w-4" />;
    case 'FAIL':
      return <X aria-hidden="true" className="h-4 w-4" />;
    default:
      return <span className="text-xs font-black tabular-nums">{displayOrder}</span>;
  }
}

function getSummaryPanelClass(failCount: number, reviewCount: number): string {
  if (failCount > 0) return 'bg-danger-soft';
  if (reviewCount > 0) return 'bg-warning-soft';
  return 'bg-primary-soft';
}

function getSummaryTitle(failCount: number, reviewCount: number, pendingCount: number): string {
  if (failCount > 0) return '재점검 필요';
  if (reviewCount > 0) return '확인 필요';
  if (pendingCount > 0) return '점검 진행 중';
  return '점검 항목 적합';
}

function SafetyCheckDetailPage() {
  const { inspectionId: inspectionIdParam } = useParams();
  const inspectionId = Number(inspectionIdParam);
  const { user } = useAuth();
  const [inspection, setInspection] = useState<InspectionDetail | null>(null);
  const [items, setItems] = useState<InspectionItem[]>([]);
  const [comment, setComment] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    if (!Number.isSafeInteger(inspectionId) || inspectionId < 1) {
      setError('올바르지 않은 점검입니다.');
      setIsLoading(false);
      return;
    }
    void safetyCheckApi
      .getInspection(inspectionId)
      .then((data) => {
        if (!active) return;
        setInspection(data);
        setItems(data.items);
        setComment(data.comment ?? '');
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
  }, [inspectionId]);

  const counts = useMemo(
    () => ({
      completed: items.filter((item) => item.result !== 'PENDING').length,
      fail: items.filter((item) => item.result === 'FAIL').length,
      review: items.filter((item) => item.result === 'REVIEW_REQUIRED').length,
      pending: items.filter((item) => item.result === 'PENDING').length,
    }),
    [items],
  );
  const canEdit = inspection?.status === 'DRAFT' && inspection.inspectorId === user?.id;
  const inspectorName =
    inspection?.inspectorId === user?.id && user ? user.name : `점검자 #${inspection?.inspectorId}`;

  function updateItem(id: number, values: Partial<Pick<InspectionItem, 'result' | 'comment'>>) {
    setItems((current) => current.map((item) => (item.id === id ? { ...item, ...values } : item)));
    setNotice(null);
  }

  async function save(status: InspectionStatus) {
    if (!inspection || !canEdit) return;
    setIsSaving(true);
    setError(null);
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
      setError(getSafetyCheckError(requestError));
    } finally {
      setIsSaving(false);
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
        <div className="grid gap-4 xl:grid-cols-[300px_minmax(0,1fr)]">
          <div className="rounded-xl border border-line bg-white p-6 shadow-card">
            <Skeleton className="h-5 w-24" />
            <div className="mt-6 space-y-5">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-2 w-full" />
              <Skeleton className="h-28 w-full" />
            </div>
          </div>
          <div className="space-y-3">
            {[0, 1, 2].map((index) => (
              <Skeleton key={index} className="h-28 w-full rounded-xl" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (!inspection) {
    return (
      <div className="mx-auto flex min-h-96 w-full max-w-[1360px] items-center justify-center px-6">
        <ErrorState message={error ?? '점검 정보를 찾을 수 없습니다.'} className="w-full" />
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
      <SafetyCheckHeader
        eyebrow="점검 상세"
        title="안전 점검·체크리스트"
        description="시뮬레이션 결과를 근거로 항목별 현장 안전 상태와 조치 내용을 기록합니다."
        backTo={`/safety-checklists/areas/${inspection.inspectionAreaId}`}
        backLabel={`${inspection.areaName} 점검 이력`}
        action={
          canEdit ? (
            <div className="flex gap-3">
              <Button
                type="button"
                variant="secondary"
                size="lg"
                onClick={() => void save('DRAFT')}
                disabled={isSaving}
              >
                임시 저장
              </Button>
              <Button
                type="button"
                size="lg"
                onClick={() => void save('COMPLETED')}
                disabled={isSaving || counts.pending > 0}
                title={counts.pending > 0 ? '모든 항목을 판정한 뒤 완료할 수 있습니다.' : undefined}
              >
                점검 완료
              </Button>
            </div>
          ) : (
            <Badge tone={inspection.status === 'COMPLETED' ? 'success' : 'neutral'}>
              {inspection.status === 'COMPLETED' ? '완료된 점검' : '읽기 전용'}
            </Badge>
          )
        }
      />

      {(error || notice) && (
        <div
          role={error ? 'alert' : 'status'}
          className={`mt-5 rounded-lg border border-line px-4 py-3 text-sm font-medium ${error ? 'bg-danger-soft text-danger-strong' : 'bg-success-soft text-success-strong'}`}
        >
          {error ?? notice}
        </div>
      )}

      <div className="mt-5 grid gap-4 xl:grid-cols-[300px_minmax(0,1fr)]">
        <Card padded={false} className="p-6">
          <h2 className="text-lg font-black text-ink">점검 정보</h2>
          <dl className="mt-6 space-y-5">
            <div>
              <dt className="text-xs font-bold text-text-muted">점검 구역</dt>
              <dd className="mt-2 rounded-lg border border-line bg-surface px-4 py-3 text-sm font-bold text-text-strong">
                {inspection.areaName}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-bold text-text-muted">점검 담당자</dt>
              <dd className="mt-2 rounded-lg border border-line bg-surface px-4 py-3 text-sm font-bold tabular-nums text-text-strong">
                {inspectorName}
              </dd>
            </div>
          </dl>

          <div className="mt-12">
            <div className="flex items-center justify-between text-xs font-bold text-text-strong">
              <span>점검 진행률</span>
              <span className="tabular-nums">
                {counts.completed} / {items.length}
              </span>
            </div>
            <div className="mt-3 h-2 overflow-hidden rounded-full bg-surface">
              <div
                className="h-full rounded-full bg-primary transition-all"
                style={{
                  width: `${items.length === 0 ? 0 : (counts.completed / items.length) * 100}%`,
                }}
              />
            </div>
          </div>

          <div
            className={`mt-8 rounded-xl border border-line p-5 ${getSummaryPanelClass(counts.fail, counts.review)}`}
          >
            <p className="text-sm font-black text-ink">
              {getSummaryTitle(counts.fail, counts.review, counts.pending)}
            </p>
            <p className="mt-3 text-xs font-bold tabular-nums text-text-strong">
              부적합 항목 {counts.fail}건 · 확인 필요 {counts.review}건
            </p>
            <Textarea
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              readOnly={!canEdit}
              aria-label="점검 종합 의견"
              placeholder="종합 의견을 입력하세요."
              className="mt-4 min-h-20 text-xs leading-5 read-only:bg-transparent"
            />
          </div>
        </Card>

        <Card padded={false} className="p-5 sm:p-7">
          <div className="flex items-end justify-between gap-4">
            <div>
              <h2 className="text-xl font-black text-ink">점검 항목</h2>
              <p className="mt-2 text-sm text-text-muted">
                각 항목의 판정과 현장 확인 내용을 기록하세요.
              </p>
            </div>
            {inspection.simulationResultId && (
              <Badge tone="neutral" className="tabular-nums">
                시뮬레이션 결과 #{inspection.simulationResultId}
              </Badge>
            )}
          </div>

          <div className="mt-6 space-y-3">
            {items.map((item) => (
              <article
                key={item.id}
                className={`rounded-xl border border-line p-5 transition-colors ${item.result === 'FAIL' ? 'bg-danger-soft' : 'bg-surface/70'}`}
              >
                <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="flex min-w-0 gap-4">
                    <div
                      aria-hidden="true"
                      className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${getResultIndicatorClass(item.result)}`}
                    >
                      {getResultIndicatorIcon(item.result, item.displayOrder)}
                    </div>
                    <div className="min-w-0">
                      <h3 className="text-sm font-black text-ink">{item.title}</h3>
                      <p className="mt-2 text-xs leading-5 text-text-muted">
                        {item.criterion ?? '별도 판정 기준 없음'}
                      </p>
                    </div>
                  </div>
                  <label className="shrink-0">
                    <span className="sr-only">{item.title} 판정</span>
                    <select
                      value={item.result}
                      onChange={(event) =>
                        updateItem(item.id, { result: event.target.value as InspectionResult })
                      }
                      disabled={!canEdit}
                      className={`h-9 min-w-28 rounded-full border px-3 text-xs font-bold outline-none focus-visible:ring-2 focus-visible:ring-focus-ring disabled:appearance-none ${RESULT_SELECT_STYLES[item.result]}`}
                    >
                      {RESULT_OPTIONS.map((result) => (
                        <option key={result} value={result}>
                          {RESULT_LABELS[result]}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                <Input
                  type="text"
                  value={item.comment ?? ''}
                  onChange={(event) => updateItem(item.id, { comment: event.target.value })}
                  readOnly={!canEdit}
                  aria-label={`${item.title} 확인 내용`}
                  placeholder="확인 내용 또는 필요한 조치를 입력하세요."
                  className="mt-4 text-xs read-only:bg-surface/60"
                />
              </article>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}

export default SafetyCheckDetailPage;
