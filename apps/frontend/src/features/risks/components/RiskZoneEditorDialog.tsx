import { useState } from 'react';
import { AttachedLawChipList } from './AttachedLawChipList';
import LawArticlePickerModal from './LawArticlePickerModal';
import { SEVERITY_OPTIONS, STATUS_OPTIONS } from '../constants/riskOptions';
import { useCreateRisk } from '../hooks/useRiskMutations';
import type { AttachedLawRef, Risk, RiskCreateRequest } from '../types/risks';
import { getRiskErrorMessage } from '../utils/getRiskErrorMessage';
import type { Bounds } from '../utils/riskZoneTypes';
import { generateRiskZoneName } from '../utils/riskZoneName';
import './RiskZoneEditorDialog.css';

interface DialogDrawing {
  width: number;
  height: number;
  layoutTexts: Array<{ text: string; x: number; y: number }>;
  zones?: Array<{ name: string; rect: Bounds }>;
}

interface Props {
  bounds: Bounds;
  drawing: DialogDrawing;
  layoutId: number;
  layoutVersionId?: number;
  onCancel: () => void;
  onConfirm: (risk: Risk) => void;
}

export function RiskZoneEditorDialog({
  bounds,
  drawing,
  layoutId,
  layoutVersionId,
  onCancel,
  onConfirm,
}: Props) {
  const [zoneName, setZoneName] = useState(() =>
    generateRiskZoneName(bounds, drawing, drawing.zones),
  );
  const [severity, setSeverity] = useState('보통');
  const [status, setStatus] = useState('임시저장');
  const [attachedLaws, setAttachedLaws] = useState<AttachedLawRef[]>([]);
  const [pickerOpen, setPickerOpen] = useState(false);

  const createMutation = useCreateRisk();

  const errorMessage = createMutation.isError ? getRiskErrorMessage(createMutation.error) : null;

  const removeAttachedLaw = (lawSerialNumber: string, lawArticleNumber: string) => {
    setAttachedLaws((prev) =>
      prev.filter(
        (item) =>
          !(item.lawSerialNumber === lawSerialNumber && item.lawArticleNumber === lawArticleNumber),
      ),
    );
  };

  const handleConfirm = () => {
    const body: RiskCreateRequest = {
      layoutId,
      layoutVersionId: layoutVersionId ?? null,
      startX: bounds.x,
      startY: bounds.y,
      endX: bounds.x + bounds.width,
      endY: bounds.y + bounds.height,
      title: zoneName.trim(),
      severity,
      status,
      description: null,
      attachedLaws,
    };
    createMutation.mutate(body, { onSuccess: onConfirm });
  };

  return (
    <>
      <div
        className="app-modal-backdrop dialog-backdrop zone-editor-backdrop"
        role="presentation"
        onMouseDown={onCancel}
      >
        <section
          className="zone-editor floating-surface"
          role="dialog"
          aria-modal="true"
          aria-labelledby="zone-editor-title"
          onMouseDown={(event) => event.stopPropagation()}
        >
          <h2 id="zone-editor-title">주의 항목 이름</h2>
          <input
            aria-label="주의 항목 이름"
            value={zoneName}
            onChange={(event) => setZoneName(event.target.value)}
            autoFocus
          />
          <label htmlFor="zone-editor-severity">심각도</label>
          <select
            id="zone-editor-severity"
            value={severity}
            onChange={(event) => setSeverity(event.target.value)}
          >
            {SEVERITY_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
          <label htmlFor="zone-editor-status">상태</label>
          <select
            id="zone-editor-status"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
          <div className="law-attach-section">
            <span className="law-attach-label">법령 첨부</span>
            {attachedLaws.length > 0 && (
              <AttachedLawChipList
                refs={attachedLaws}
                className="law-attach-chips"
                onRemove={(ref) => removeAttachedLaw(ref.lawSerialNumber, ref.lawArticleNumber)}
              />
            )}
            <button type="button" className="law-attach-button" onClick={() => setPickerOpen(true)}>
              + 법령 첨부
            </button>
          </div>
          {errorMessage && <p role="alert">{errorMessage}</p>}
          <div>
            <button type="button" onClick={onCancel}>
              취소
            </button>
            <button
              type="button"
              onClick={handleConfirm}
              disabled={createMutation.isPending || !zoneName.trim()}
            >
              {createMutation.isPending ? '등록 중...' : '확정'}
            </button>
          </div>
        </section>
      </div>
      {pickerOpen && (
        <LawArticlePickerModal
          open
          layer="nested"
          onClose={() => setPickerOpen(false)}
          selected={attachedLaws}
          onConfirm={(refs) => {
            setAttachedLaws(refs);
            setPickerOpen(false);
          }}
        />
      )}
    </>
  );
}
