import type { Dispatch } from 'react';
import type { EditorState, Tool } from '../types';
import type { EditorAction } from '../state/editorReducer';

interface ToolToolbarProps {
  state: EditorState;
  dispatch: Dispatch<EditorAction>;
  className?: string;
  /** 기하 도구 비활성(잠긴 도면 버전). 구역 도구는 여기에 영향받지 않는다. */
  disabled?: boolean;
  /** 구역 도구 비활성(구역 관리 권한 없음). */
  zoneDisabled?: boolean;
}

const TOOLS: Array<{ id: Tool; label: string }> = [
  { id: 'select', label: '선택' },
  { id: 'wall', label: '벽' },
  { id: 'outsideWall', label: '외곽벽' },
  { id: 'exit', label: '비상구' },
  { id: 'pillar', label: '기둥' },
  { id: 'fabric', label: '구조물' },
  { id: 'zone', label: '구역' },
  { id: 'text', label: '텍스트' },
  { id: 'erase', label: '지우개' },
];

export function ToolToolbar({
  state,
  dispatch,
  className,
  disabled = false,
  zoneDisabled = false,
}: ToolToolbarProps) {
  return (
    <div className={`layout-tool-toolbar ${className ?? ''}`}>
      {TOOLS.map((tool) => {
        const active = state.tool === tool.id;
        return (
          <button
            key={tool.id}
            type="button"
            aria-pressed={active}
            disabled={tool.id === 'zone' ? zoneDisabled : disabled}
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
