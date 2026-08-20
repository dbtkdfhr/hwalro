import { useRef, useState } from 'react';
import { Pencil } from 'lucide-react';
import { CanvasWorkspaceHeader } from '../../../components/workspace';

interface LayoutWorkspaceHeaderProps {
  name: string;
  readOnly: boolean;
  onRename: (name: string) => void;
}

export function LayoutWorkspaceHeader({ name, readOnly, onRename }: LayoutWorkspaceHeaderProps) {
  const [editing, setEditing] = useState(false);
  const [draftName, setDraftName] = useState('');
  const editingRef = useRef(false);

  const startEditing = () => {
    editingRef.current = true;
    setDraftName(name);
    setEditing(true);
  };

  const endEditing = (commit: boolean) => {
    if (!editingRef.current) return;
    editingRef.current = false;
    setEditing(false);
    if (!commit) return;

    const nextName = draftName.trim();
    if (nextName !== '' && nextName !== name) onRename(nextName);
  };

  const title = editing ? (
    <input
      autoFocus
      type="text"
      value={draftName}
      onChange={(event) => setDraftName(event.currentTarget.value)}
      onBlur={() => endEditing(true)}
      onKeyDown={(event) => {
        if (event.key === 'Enter') endEditing(true);
        if (event.key === 'Escape') endEditing(false);
      }}
      aria-label="도면 이름"
      maxLength={50}
      className="layout-workspace-title-input"
    />
  ) : (
    <h1 className="layout-workspace-title">
      <span className="layout-workspace-title__text">{name}</span>
      {!readOnly ? (
        <button type="button" onClick={startEditing} aria-label="도면 이름 수정">
          <Pencil aria-hidden="true" strokeWidth={1.8} />
        </button>
      ) : null}
    </h1>
  );

  return (
    <CanvasWorkspaceHeader
      title={title}
      subtitle="도면 배치 편집"
      status={readOnly ? '편집 잠김' : '편집 가능'}
      statusTone={readOnly ? 'locked' : 'editing'}
    />
  );
}
