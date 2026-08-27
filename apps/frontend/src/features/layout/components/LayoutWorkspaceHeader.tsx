import { useState } from 'react';
import { Pencil } from 'lucide-react';
import { CanvasWorkspaceHeader } from '../../../components/workspace';
import DrawingInfoEditDialog from './DrawingInfoEditDialog';

interface LayoutWorkspaceHeaderProps {
  name: string;
  description: string | null;
  readOnly: boolean;
  onUpdateInfo: (info: { title: string; description: string | null }) => void;
}

export function LayoutWorkspaceHeader({
  name,
  description,
  readOnly,
  onUpdateInfo,
}: LayoutWorkspaceHeaderProps) {
  const [dialogOpen, setDialogOpen] = useState(false);

  const title = (
    <h1 className="layout-workspace-title">
      <span className="layout-workspace-title__text">{name}</span>
      {!readOnly ? (
        <button type="button" onClick={() => setDialogOpen(true)} aria-label="도면 정보 수정">
          <Pencil aria-hidden="true" strokeWidth={1.8} />
        </button>
      ) : null}
    </h1>
  );

  return (
    <>
      <CanvasWorkspaceHeader
        title={title}
        subtitle="도면 배치 편집"
        status={readOnly ? '편집 잠김' : '편집 가능'}
        statusTone={readOnly ? 'locked' : 'editing'}
      />
      <DrawingInfoEditDialog
        open={dialogOpen}
        initialTitle={name}
        initialDescription={description ?? ''}
        onClose={() => setDialogOpen(false)}
        onSubmit={(info) => onUpdateInfo(info)}
      />
    </>
  );
}
