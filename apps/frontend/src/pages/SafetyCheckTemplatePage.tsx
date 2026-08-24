import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { ArrowDown, ArrowUp, ListChecks, Trash2 } from 'lucide-react';
import { useAuth } from '../features/auth/context/AuthContext';
import { safetyCheckApi } from '../features/safetyChecks/api/safetyCheckApi';
import type { InspectionArea } from '../features/safetyChecks/types';
import { getSafetyCheckError } from '../features/safetyChecks/utils';
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Field,
  Input,
  Select,
  Skeleton,
  Textarea,
} from '../components/ui';
import SafetyCheckHeader from './safetyChecks/SafetyCheckHeader';

interface EditableItem {
  key: string;
  title: string;
  criterion: string;
  category: string;
}

const CATEGORY_OPTIONS = [
  { value: 'EVACUATION', label: '피난·대피' },
  { value: 'FIRE', label: '화재·방재' },
  { value: 'STRUCTURE', label: '구조물' },
  { value: 'GUIDANCE', label: '안내·유도' },
  { value: 'CONTROL', label: '접근 통제' },
  { value: 'SIMULATION', label: '시뮬레이션' },
  { value: 'OTHER', label: '기타' },
];

let temporaryItemSequence = 0;

function createTemporaryItemKey(): string {
  temporaryItemSequence += 1;
  return `checklist-item-${Date.now()}-${temporaryItemSequence}`;
}

function createEmptyItem(): EditableItem {
  return {
    key: createTemporaryItemKey(),
    title: '',
    criterion: '',
    category: 'EVACUATION',
  };
}

function SafetyCheckTemplatePage() {
  const { areaId: areaIdParam } = useParams();
  const areaId = Number(areaIdParam);
  const { user } = useAuth();
  const [area, setArea] = useState<InspectionArea | null>(null);
  const [version, setVersion] = useState(0);
  const [items, setItems] = useState<EditableItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const hasManagePermission =
    user?.roles.includes('ADMIN') || user?.roles.includes('SAFETY_REVIEWER');
  const canManage = hasManagePermission && area?.active === true;

  useEffect(() => {
    let active = true;
    if (!Number.isSafeInteger(areaId) || areaId < 1) {
      setError('올바르지 않은 점검 구역입니다.');
      setIsLoading(false);
      return;
    }

    void Promise.all([safetyCheckApi.getArea(areaId), safetyCheckApi.getChecklistTemplate(areaId)])
      .then(([inspectionArea, template]) => {
        if (!active) return;
        setArea(inspectionArea);
        setVersion(template.version);
        setItems(
          template.items.length > 0
            ? template.items.map((item) => ({
                key: `template-item-${item.id}`,
                title: item.title,
                criterion: item.criterion ?? '',
                category: item.category,
              }))
            : [createEmptyItem()],
        );
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

  function updateItem(key: string, values: Partial<Omit<EditableItem, 'key'>>) {
    setItems((current) =>
      current.map((item) => (item.key === key ? { ...item, ...values } : item)),
    );
    setNotice(null);
  }

  function moveItem(index: number, direction: -1 | 1) {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= items.length) return;
    setItems((current) => {
      const next = [...current];
      [next[index], next[targetIndex]] = [next[targetIndex], next[index]];
      return next;
    });
    setNotice(null);
  }

  function removeItem(key: string) {
    setItems((current) => current.filter((item) => item.key !== key));
    setNotice(null);
  }

  async function saveTemplate() {
    if (!canManage) return;
    if (items.length === 0 || items.some((item) => !item.title.trim())) {
      setError('모든 점검 항목의 이름을 입력하세요.');
      return;
    }

    setIsSaving(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await safetyCheckApi.updateChecklistTemplate(areaId, {
        items: items.map((item) => ({
          title: item.title.trim(),
          criterion: item.criterion.trim() || null,
          category: item.category,
        })),
      });
      setVersion(updated.version);
      setItems(
        updated.items.map((item) => ({
          key: `template-item-${item.id}`,
          title: item.title,
          criterion: item.criterion ?? '',
          category: item.category,
        })),
      );
      setNotice(`체크리스트 v${updated.version}을 활성화했습니다. 새 점검부터 적용됩니다.`);
    } catch (requestError) {
      setError(getSafetyCheckError(requestError));
    } finally {
      setIsSaving(false);
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
        <div className="rounded-xl border border-line bg-surface-raised p-5 shadow-neu-raised sm:p-7">
          <div className="flex items-end justify-between gap-4 border-b border-line pb-5">
            <div className="space-y-2">
              <Skeleton className="h-6 w-40" />
              <Skeleton className="h-4 w-64" />
            </div>
            <Skeleton className="h-10 w-24" />
          </div>
          <div className="mt-6 space-y-4">
            {[0, 1].map((index) => (
              <Skeleton key={index} className="h-56 w-full rounded-xl" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
      <SafetyCheckHeader
        eyebrow="체크리스트 설정"
        title={`${area?.name ?? '점검 구역'} 점검 항목`}
        description="항목을 저장하면 새 템플릿 버전이 생성되며, 기존 점검 이력은 변경되지 않습니다."
        backTo={`/safety-checklists/areas/${areaId}`}
        backLabel="점검 이력으로 돌아가기"
        action={
          canManage ? (
            <Button
              type="button"
              size="lg"
              onClick={() => void saveTemplate()}
              disabled={isSaving || items.length === 0}
              isLoading={isSaving}
            >
              새 버전으로 저장
            </Button>
          ) : undefined
        }
      />

      {error && <ErrorState message={error} className="mt-5" />}

      {notice && (
        <div
          role="status"
          className="mt-5 rounded-lg border border-line bg-success-soft px-4 py-3 text-sm font-bold text-success-strong"
        >
          {notice}
        </div>
      )}

      {!hasManagePermission && (
        <div className="mt-5 rounded-lg border border-line bg-warning-soft px-4 py-3 text-sm font-bold text-warning-strong">
          점검 항목을 수정할 권한이 없습니다.
        </div>
      )}

      {hasManagePermission && area && !area.active && (
        <div className="mt-5 rounded-lg border border-line bg-warning-soft px-4 py-3 text-sm font-bold text-warning-strong">
          삭제된 점검 구역의 항목은 수정할 수 없습니다.
        </div>
      )}

      <Card padded={false} className="mt-5 overflow-hidden bg-surface-raised shadow-neu-raised">
        <div className="flex items-end justify-between gap-4 border-b border-line px-5 py-4 sm:px-7">
          <div>
            <h2 className="text-xl font-black text-ink">항목 구성</h2>
            <p className="mt-2 text-sm text-text-muted">
              현재 버전 <span className="tabular-nums">v{version}</span> · 총{' '}
              <span className="tabular-nums">{items.length}</span>개 항목
            </p>
          </div>
          {canManage && (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => setItems((current) => [...current, createEmptyItem()])}
            >
              항목 추가
            </Button>
          )}
        </div>

        <div className="space-y-4 p-5 sm:p-7">
          {items.length === 0 && (
            <EmptyState
              icon={ListChecks}
              title="등록된 점검 항목이 없습니다."
              description="상단의 ‘항목 추가’ 버튼으로 새 점검 항목을 추가하세요."
            />
          )}
          {items.map((item, index) => {
            const knownCategory = CATEGORY_OPTIONS.some((option) => option.value === item.category);
            return (
              <article
                key={item.key}
                className="rounded-xl border border-line bg-surface-sunken p-5 shadow-neu-pressed"
              >
                <div className="flex items-center justify-between gap-4">
                  <div className="flex items-center gap-3">
                    <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-soft text-sm font-black tabular-nums text-primary">
                      {index + 1}
                    </span>
                    <p className="text-sm font-black text-ink">점검 항목</p>
                  </div>
                  {canManage && (
                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => moveItem(index, -1)}
                        disabled={index === 0}
                        aria-label={`${index + 1}번 항목 위로 이동`}
                        className="flex h-8 w-8 items-center justify-center rounded-md bg-surface-raised text-text-muted shadow-neu-raised transition-[background-color,box-shadow] hover:bg-surface-overlay active:shadow-neu-pressed disabled:opacity-30"
                      >
                        <ArrowUp aria-hidden="true" className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => moveItem(index, 1)}
                        disabled={index === items.length - 1}
                        aria-label={`${index + 1}번 항목 아래로 이동`}
                        className="flex h-8 w-8 items-center justify-center rounded-md bg-surface-raised text-text-muted shadow-neu-raised transition-[background-color,box-shadow] hover:bg-surface-overlay active:shadow-neu-pressed disabled:opacity-30"
                      >
                        <ArrowDown aria-hidden="true" className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => removeItem(item.key)}
                        aria-label={`${index + 1}번 항목 삭제`}
                        className="flex h-8 items-center gap-1 rounded-md px-2 text-xs font-bold text-danger transition hover:bg-danger-soft"
                      >
                        <Trash2 aria-hidden="true" className="h-3.5 w-3.5" />
                        삭제
                      </button>
                    </div>
                  )}
                </div>

                <div className="mt-5 grid gap-4 sm:grid-cols-[minmax(0,1fr)_12rem]">
                  <Field label="항목명" htmlFor={`${item.key}-title`}>
                    <Input
                      id={`${item.key}-title`}
                      type="text"
                      value={item.title}
                      onChange={(event) => updateItem(item.key, { title: event.target.value })}
                      readOnly={!canManage}
                      maxLength={200}
                      placeholder="점검 항목명을 입력하세요."
                    />
                  </Field>
                  <Field label="분류">
                    <Select
                      value={item.category}
                      onChange={(event) => updateItem(item.key, { category: event.target.value })}
                      disabled={!canManage}
                    >
                      {!knownCategory && <option value={item.category}>{item.category}</option>}
                      {CATEGORY_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </Select>
                  </Field>
                </div>
                <label className="mt-4 block text-sm font-bold text-text-strong">
                  판정 기준
                  <Textarea
                    value={item.criterion}
                    onChange={(event) => updateItem(item.key, { criterion: event.target.value })}
                    readOnly={!canManage}
                    placeholder="현장에서 확인할 구체적인 기준을 입력하세요."
                    className="mt-1.5 text-sm leading-6"
                  />
                </label>
              </article>
            );
          })}
        </div>
      </Card>
    </div>
  );
}

export default SafetyCheckTemplatePage;
