import { useState } from 'react';
import type { AttachedLawRef, RiskCreateRequest, RiskUpdateRequest } from '../types/risks';

export interface RiskFormValues {
  title: string;
  severity: string;
  status: string;
  description: string;
  attachedLaws?: AttachedLawRef[];
}

export function useRiskForm(initial: RiskFormValues) {
  const [title, setTitle] = useState(initial.title);
  const [severity, setSeverity] = useState(initial.severity);
  const [status, setStatus] = useState(initial.status);
  const [description, setDescription] = useState(initial.description);
  const [attachedLaws, setAttachedLaws] = useState<AttachedLawRef[]>(initial.attachedLaws ?? []);

  const toggleAttachedLaw = (ref: AttachedLawRef) => {
    setAttachedLaws((prev) =>
      prev.some(
        (item) =>
          item.lawSerialNumber === ref.lawSerialNumber &&
          item.lawArticleNumber === ref.lawArticleNumber,
      )
        ? prev.filter(
            (item) =>
              !(
                item.lawSerialNumber === ref.lawSerialNumber &&
                item.lawArticleNumber === ref.lawArticleNumber
              ),
          )
        : [...prev, ref],
    );
  };

  const removeAttachedLaw = (lawSerialNumber: string, lawArticleNumber: string) => {
    setAttachedLaws((prev) =>
      prev.filter(
        (item) =>
          !(item.lawSerialNumber === lawSerialNumber && item.lawArticleNumber === lawArticleNumber),
      ),
    );
  };

  const toCreateRequest = (): Omit<RiskCreateRequest, 'layoutId'> => ({
    layoutVersionId: null,
    startX: null,
    startY: null,
    endX: null,
    endY: null,
    title: title.trim(),
    severity: severity.trim(),
    status: status.trim(),
    description: description.trim() === '' ? null : description.trim(),
    attachedLaws,
  });

  const toUpdateRequest = (): RiskUpdateRequest => ({
    title: title.trim(),
    severity: severity.trim(),
    status: status.trim(),
    description: description.trim() === '' ? null : description.trim(),
    attachedLaws,
  });

  return {
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
    toggleAttachedLaw,
    removeAttachedLaw,
    toCreateRequest,
    toUpdateRequest,
  };
}
