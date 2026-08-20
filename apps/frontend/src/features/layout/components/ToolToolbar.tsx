import type { Dispatch } from 'react';
import type { EditorState, Tool } from '../types';
import type { EditorAction } from '../state/editorReducer';

interface ToolToolbarProps {
  state: EditorState;
  dispatch: Dispatch<EditorAction>;
  className?: string;
  disabled?: boolean;
}

const TOOLS: Array<{ id: Tool; label: string }> = [
  { id: 'select', label: '선택' },
  { id: 'wall', label: '벽' },
  { id: 'outsideWall', label: '외각벽' },
  { id: 'exit', label: '비상구' },
  { id: 'pillar', label: '기둥' },
  { id: 'fabric', label: '구조물' },
  { id: 'text', label: '텍스트' },
  { id: 'erase', label: '지우개' },
  { id: 'background', label: '배경' },
];

export function ToolToolbar({ state, dispatch, className, disabled = false }: ToolToolbarProps) {
  return (
    <div className={`layout-tool-toolbar ${className ?? ''}`}>
      {TOOLS.map((tool) => {
        const active = state.tool === tool.id;
        return (
          <button
            key={tool.id}
            type="button"
            aria-pressed={active}
            disabled={disabled}
            onClick={() => dispatch({ type: 'setTool', tool: tool.id })}
            className={`layout-tool-button ${active ? 'is-active' : ''}`}
          >
            {tool.label}
          </button>
        );
      })}
    </div>
  );
}
