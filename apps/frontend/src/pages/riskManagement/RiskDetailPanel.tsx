import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  ConfirmDialog,
  ErrorState,
  Field,
  Input,
  Select,
  Skeleton,
  Textarea,
} from '../../components/ui';
import { AttachedLawChipList } from '../../features/risks/components/AttachedLawChipList';
import LawArticlePickerModal from '../../features/risks/components/LawArticlePickerModal';
import { riskApi } from '../../features/risks/api/riskApi';
import { RiskZonePreview } from '../../features/risks/components/RiskZonePreview';
import { SEVERITY_OPTIONS, STATUS_OPTIONS } from '../../features/risks/constants/riskOptions';
import { useRiskForm } from '../../features/risks/hooks/useRiskForm';
import { useDeleteRisk, useUpdateRisk } from '../../features/risks/hooks/useRiskMutations';
import type { Risk } from '../../features/risks/types/risks';
import { getRiskErrorMessage } from '../../features/risks/utils/getRiskErrorMessage';

function RiskDetailPanel({ risk }: { risk: Risk }) {
  const navigate = useNavigate();
  const {
    title,
    setTitle,
    severity,
    setSeverity,
    status,
    setStatus,
    description,
    setDescription,
    attachedLaws,
    setAttachedLaws,
    removeAttachedLaw,
    toUpdateRequest,
  } = useRiskForm({
    title: risk.title,
    severity: risk.severity,
    status: risk.status,
    description: risk.description ?? '',
    attachedLaws: risk.attachedLaws,
  });

  const [pickerOpen, setPickerOpen] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);

  const updateMutation = useUpdateRisk();
  const deleteMutation = useDeleteRisk();

  const zoneBounds = useMemo(() => {
    if (
      risk.simulationResultId === null ||
      risk.startX === null ||
      risk.startY === null ||
      risk.endX === null ||
      risk.endY === null
    ) {
      return null;
    }
    return {
      simulationResultId: risk.simulationResultId,
      startX: risk.startX,
      startY: risk.startY,
      endX: risk.endX,
      endY: risk.endY,
    };
  }, [risk.endX, risk.endY, risk.simulationResultId, risk.startX, risk.startY]);

  const simulationResultId = zoneBounds?.simulationResultId;
  const drawingContextQuery = useQuery({
    queryKey: ['risk-drawing', simulationResultId],
    queryFn: () => {
      if (simulationResultId === undefined) {
        throw new Error('연결된 시뮬레이션 결과가 없습니다.');
      }
      return riskApi.getDrawingContext(simulationResultId);
    },
    enabled: simulationResultId !== undefined,
  });

  const errorMessage = updateMutation.isError
    ? getRiskErrorMessage(updateMutation.error)
    : deleteMutation.isError
      ? getRiskErrorMessage(deleteMutation.error)
      : null;

  const handleSave = () => {
    updateMutation.mutate({ id: risk.id, body: toUpdateRequest() });
  };

  const handleDelete = () => {
    setDeleteConfirmOpen(true);
  };

  const handleOpenSimulation = () => {
    if (drawingContextQuery?.data) {
      navigate(`/simulations/${drawingContextQuery?.data.simulationId}/results`);
    }
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-3">
        <div className="w-40">
          <Select
            aria-label="위험도"
            value={severity}
            onChange={(event) => setSeverity(event.target.value)}
          >
            {SEVERITY_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </Select>
        </div>
        <div className="ml-auto w-40">
          <Select
            aria-label="상태"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </Select>
        </div>
      </div>

      <div className="mt-6 space-y-6">
        <Field label="위험 항목명" htmlFor="risk-title">
          <Input id="risk-title" value={title} onChange={(event) => setTitle(event.target.value)} />
        </Field>
        <Field label="설명" htmlFor="risk-description">
          <Textarea
            id="risk-description"
            rows={4}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
        </Field>
        <section className="space-y-2" aria-label="법령 첨부">
          <span className="block text-sm font-bold text-text-strong">법령 첨부</span>
          {attachedLaws.length > 0 && (
            <AttachedLawChipList
              refs={attachedLaws}
              onRemove={(ref) => removeAttachedLaw(ref.lawSerialNumber, ref.lawArticleNumber)}
            />
          )}
          <Button type="button" variant="secondary" size="sm" onClick={() => setPickerOpen(true)}>
            + 법령 첨부
          </Button>
        </section>
      </div>

      {zoneBounds && (
        <section className="mt-6" aria-label="시뮬레이션 구역">
          <span className="text-xs font-bold text-text-muted">시뮬레이션 구역</span>
          {drawingContextQuery.isPending ? (
            <Skeleton className="mt-2 h-40 w-full" />
          ) : drawingContextQuery.isError ? (
            <div className="mt-2">
              <ErrorState message={getRiskErrorMessage(drawingContextQuery.error)} />
            </div>
          ) : (
            drawingContextQuery?.data && (
              <div className="mt-2 space-y-2">
                <button
                  type="button"
                  onClick={handleOpenSimulation}
                  title="시뮬레이션 결과 페이지로 이동"
                  className="block w-full cursor-pointer overflow-hidden rounded-xl border border-line bg-surface-sunken text-left shadow-neu-pressed outline-none transition-[border-color,box-shadow] hover:border-primary focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus-ring"
                >
                  <RiskZonePreview drawing={drawingContextQuery?.data.drawing} zone={zoneBounds} />
                </button>
                <button
                  type="button"
                  onClick={handleOpenSimulation}
                  className="flex h-10 w-full cursor-pointer items-center justify-center gap-1 overflow-hidden rounded-lg border border-primary bg-surface-raised px-4 text-sm font-bold text-primary shadow-neu-raised outline-none transition-[background-color,box-shadow] hover:bg-primary-soft active:shadow-neu-pressed focus-visible:ring-2 focus-visible:ring-focus-ring"
                >
                  <span className="truncate">
                    {drawingContextQuery?.data.title || '시뮬레이션 결과'}
                  </span>
                  <span className="shrink-0">보러가기</span>
                </button>
              </div>
            )
          )}
        </section>
      )}

      {errorMessage && (
        <div className="mt-5">
          <ErrorState message={errorMessage} />
        </div>
      )}

      <div className="mt-6 grid grid-cols-2 gap-3 pb-1">
        <Button
          variant="primary"
          size="lg"
          onClick={handleSave}
          disabled={updateMutation.isPending}
          className="w-full"
        >
          {updateMutation.isPending ? '저장 중...' : '저장'}
        </Button>
        <Button
          variant="secondary"
          size="lg"
          onClick={handleDelete}
          disabled={deleteMutation.isPending}
          className="w-full"
        >
          {deleteMutation.isPending ? '삭제 중...' : '삭제'}
        </Button>
      </div>
      {pickerOpen && (
        <LawArticlePickerModal
          open
          onClose={() => setPickerOpen(false)}
          selected={attachedLaws}
          onConfirm={(refs) => {
            setAttachedLaws(refs);
            setPickerOpen(false);
          }}
        />
      )}
      <ConfirmDialog
        open={deleteConfirmOpen}
        title="위험 항목 삭제"
        description={`'${title}' 위험 항목을 삭제하시겠습니까?`}
        isLoading={deleteMutation.isPending}
        onCancel={() => {
          setDeleteConfirmOpen(false);
          deleteMutation.reset();
        }}
        onConfirm={() =>
          deleteMutation.mutate(risk.id, {
            onSuccess: () => setDeleteConfirmOpen(false),
          })
        }
      >
        <p className="text-sm text-text-muted">삭제한 위험 항목은 복구할 수 없습니다.</p>
        {deleteMutation.isError && (
          <p
            role="alert"
            className="mt-4 rounded-lg border border-danger/25 bg-danger-soft px-3 py-2 text-sm text-danger-strong"
          >
            {getRiskErrorMessage(deleteMutation.error)}
          </p>
        )}
      </ConfirmDialog>
    </div>
  );
}

export default RiskDetailPanel;
