import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Button, ErrorState, Field, Input, Modal, Select, Textarea } from '../../components/ui';
import { AttachedLawChipList } from '../../features/risks/components/AttachedLawChipList';
import LawArticlePickerModal from '../../features/risks/components/LawArticlePickerModal';
import { SEVERITY_OPTIONS, STATUS_OPTIONS } from '../../features/risks/constants/riskOptions';
import { useRiskForm } from '../../features/risks/hooks/useRiskForm';
import { useCreateRisk } from '../../features/risks/hooks/useRiskMutations';
import type { RegulationDetail } from '../../features/risks/types/regulations';
import type { AttachedLawRef } from '../../features/risks/types/risks';
import { getRiskErrorMessage } from '../../features/risks/utils/getRiskErrorMessage';

function RiskCreateDialog({ onClose }: { onClose: () => void }) {
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
    toCreateRequest,
  } = useRiskForm({
    title: '',
    severity: '보통',
    status: '임시저장',
    description: '',
    attachedLaws: [],
  });

  const queryClient = useQueryClient();
  const createMutation = useCreateRisk();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [attachedLawNames, setAttachedLawNames] = useState<Map<string, string>>(new Map());

  const errorMessage = createMutation.isError ? getRiskErrorMessage(createMutation.error) : null;

  const handleConfirmLaws = (refs: AttachedLawRef[]) => {
    const nextNames = new Map(attachedLawNames);
    for (const ref of refs) {
      const key = `${ref.lawSerialNumber}:${ref.lawArticleNumber}`;
      if (nextNames.has(key)) continue;
      const detail = queryClient.getQueryData<RegulationDetail>([
        'law-detail',
        ref.lawSerialNumber,
      ]);
      if (detail) {
        const article = detail.articles.find((item) => item.number === ref.lawArticleNumber);
        nextNames.set(
          key,
          article
            ? `${detail.name} 제${article.number}조${article.title ? ` (${article.title})` : ''}`
            : detail.name,
        );
      }
    }
    setAttachedLawNames(nextNames);
    setAttachedLaws(refs);
    setPickerOpen(false);
  };

  const handleRemoveLaw = (ref: AttachedLawRef) => {
    removeAttachedLaw(ref.lawSerialNumber, ref.lawArticleNumber);
    setAttachedLawNames((prev) => {
      const next = new Map(prev);
      next.delete(`${ref.lawSerialNumber}:${ref.lawArticleNumber}`);
      return next;
    });
  };

  const handleSubmit = () => {
    createMutation.mutate(toCreateRequest(), { onSuccess: onClose });
  };

  return (
    <>
      <Modal
        open
        onClose={onClose}
        title="위험 예상 항목 등록"
        size="md"
        footer={
          <>
            <Button type="button" variant="secondary" onClick={onClose}>
              취소
            </Button>
            <Button
              type="button"
              onClick={handleSubmit}
              disabled={createMutation.isPending || !title.trim()}
            >
              {createMutation.isPending ? '등록 중...' : '등록'}
            </Button>
          </>
        }
      >
        <div className="space-y-5">
          <Field label="위험 항목명" htmlFor="risk-create-name" required>
            <Input
              id="risk-create-name"
              required
              autoFocus
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="예: 중앙 통로 밀집도 초과"
            />
          </Field>
          <Field label="심각도" htmlFor="risk-create-severity">
            <Select
              id="risk-create-severity"
              value={severity}
              onChange={(event) => setSeverity(event.target.value)}
            >
              {SEVERITY_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="상태" htmlFor="risk-create-status">
            <Select
              id="risk-create-status"
              value={status}
              onChange={(event) => setStatus(event.target.value)}
            >
              {STATUS_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="설명" htmlFor="risk-create-description">
            <Textarea
              id="risk-create-description"
              rows={4}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="선택"
            />
          </Field>
          <section className="space-y-2" aria-label="법령 첨부">
            <span className="block text-sm font-bold text-text-strong">법령 첨부</span>
            {attachedLaws.length > 0 && (
              <AttachedLawChipList
                refs={attachedLaws}
                names={attachedLawNames}
                onRemove={handleRemoveLaw}
              />
            )}
            <Button type="button" variant="secondary" size="sm" onClick={() => setPickerOpen(true)}>
              + 법령 첨부
            </Button>
          </section>
          {errorMessage && <ErrorState message={errorMessage} />}
        </div>
      </Modal>
      {pickerOpen && (
        <LawArticlePickerModal
          open
          onClose={() => setPickerOpen(false)}
          selected={attachedLaws}
          onConfirm={handleConfirmLaws}
        />
      )}
    </>
  );
}

export default RiskCreateDialog;
