import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Button, Field, Input, Modal, Textarea } from '../../../components/ui';

export interface DrawingInfoDraft {
  title: string;
  description: string;
}

interface DrawingInfoEditDialogProps {
  open: boolean;
  initialTitle: string;
  initialDescription: string;
  onClose: () => void;
  onSubmit: (draft: DrawingInfoDraft) => void;
}

const TITLE_MAX_LENGTH = 200;
const DESCRIPTION_MAX_LENGTH = 10000;

function DrawingInfoEditDialog({
  open,
  initialTitle,
  initialDescription,
  onClose,
  onSubmit,
}: DrawingInfoEditDialogProps) {
  const [title, setTitle] = useState(initialTitle);
  const [description, setDescription] = useState(initialDescription);

  useEffect(() => {
    if (!open) return;
    setTitle(initialTitle);
    setDescription(initialDescription);
  }, [open, initialTitle, initialDescription]);

  const canSubmit = title.trim() !== '';

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSubmit) return;
    onSubmit({ title: title.trim(), description: description.trim() });
    onClose();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="도면 정보 수정"
      description="도면명과 설명을 수정할 수 있습니다."
    >
      <form onSubmit={handleSubmit} noValidate>
        <Field label="도면명">
          <Input
            autoFocus
            type="text"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={TITLE_MAX_LENGTH}
            aria-required="true"
          />
        </Field>
        <div className="mt-4">
          <Field label="설명">
            <Textarea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="도면에 대한 설명을 입력해 주세요. (선택)"
              maxLength={DESCRIPTION_MAX_LENGTH}
              rows={4}
            />
          </Field>
        </div>
        <div className="mt-6 flex items-center justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" disabled={!canSubmit}>
            저장
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default DrawingInfoEditDialog;
