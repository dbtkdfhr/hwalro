import { useCallback, useEffect, useLayoutEffect, useReducer, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { LayoutCanvas } from '../components/LayoutCanvas';
import { LayoutToolbar } from '../components/LayoutToolbar';
import { LayoutWorkspaceHeader } from '../components/LayoutWorkspaceHeader';
import { ToolToolbar } from '../components/ToolToolbar';
import { ZoomControl } from '../components/ZoomControl';
import { SettingsPanel } from '../components/SettingsPanel';
import { InlineTextInput } from '../components/InlineTextInput';
import { Button } from '../../../components/ui';
import {
  CanvasWorkspace,
  CanvasWorkspaceBackButton,
  CanvasWorkspacePanel,
  CanvasWorkspacePanelRestore,
  CanvasWorkspaceState,
  useCollapsibleWorkspacePanel,
} from '../../../components/workspace';
import { createInitialState, editorReducer } from '../state/editorReducer';
import { fetchDrawing, saveDrawing } from '../api/layoutApi';
import type { DrawingSession } from '../api/layoutApi';
import type { ValidationProblem, ValidationProblemKind } from '../types';
import { CreateSimulationDraftDialog } from '../../simulations/components/CreateSimulationDraftDialog';
import { simulationApi } from '../../simulations/api/simulationApi';
import { getSimulationErrorMessage } from '../../simulations/utils/getSimulationErrorMessage';
import { getDrawingErrorMessage } from '../../drawings/utils/getDrawingErrorMessage';
import { useRecordLastActivity } from '../../home/hooks/useRecordLastActivity';
import '../layout.css';

type LoadStatus = 'loading' | 'ready' | 'missing' | 'error';
type SaveStatus = 'idle' | 'saving' | 'saved' | 'error';

const VALIDATION_KINDS: ValidationProblemKind[] = [
  'wall',
  'outsideWall',
  'exit',
  'pillar',
  'fabric',
];

function parseValidationProblems(data: unknown): ValidationProblem[] {
  const raw = (data as { problems?: unknown } | undefined)?.problems;
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.filter(
    (entry): entry is ValidationProblem =>
      typeof entry === 'object' &&
      entry !== null &&
      typeof (entry as ValidationProblem).name === 'string' &&
      (VALIDATION_KINDS as string[]).includes((entry as ValidationProblem).kind),
  );
}

function LayoutPage() {
  const { drawingId = '' } = useParams();
  const navigate = useNavigate();
  const recordLastActivity = useRecordLastActivity();
  const [state, dispatch] = useReducer(editorReducer, undefined, createInitialState);
  const [loadStatus, setLoadStatus] = useState<LoadStatus>('loading');
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle');
  const [size, setSize] = useState({ w: 0, h: 0 });
  const [retryCount, setRetryCount] = useState(0);
  const settingsPanel = useCollapsibleWorkspacePanel();
  const [draftDialogOpen, setDraftDialogOpen] = useState(false);
  const [draftPending, setDraftPending] = useState(false);
  const stateRef = useRef(state);
  const sessionRef = useRef<DrawingSession | null>(null);
  const loadedRef = useRef(false);
  const saveTimerRef = useRef<number | null>(null);
  const collapseButtonRef = useRef<HTMLButtonElement>(null);
  const restoreButtonRef = useRef<HTMLButtonElement>(null);
  const restorePanelFocusRef = useRef(false);

  useLayoutEffect(() => {
    stateRef.current = state;
  }, [state]);

  useLayoutEffect(() => {
    if (!restorePanelFocusRef.current) {
      return;
    }
    if (settingsPanel.isMinimized) {
      restoreButtonRef.current?.focus();
      restorePanelFocusRef.current = false;
    } else if (settingsPanel.isExpanding) {
      collapseButtonRef.current?.focus();
      restorePanelFocusRef.current = false;
    }
  }, [settingsPanel.isExpanding, settingsPanel.isMinimized]);

  const onSizeChange = useCallback((next: { w: number; h: number }) => {
    setSize(next);
  }, []);

  useEffect(() => {
    if (loadedRef.current) {
      return;
    }
    loadedRef.current = true;
    let cancelled = false;
    sessionRef.current = null;
    setLoadStatus('loading');
    fetchDrawing(drawingId)
      .then((session) => {
        if (cancelled) {
          return;
        }
        if (session === null) {
          setLoadStatus('missing');
          return;
        }
        sessionRef.current = session;
        dispatch({ type: 'loadDocument', doc: session.doc });
        setLoadStatus('ready');
      })
      .catch(() => {
        if (!cancelled) {
          setLoadStatus('error');
        }
      });
    return () => {
      cancelled = true;
      loadedRef.current = false;
    };
  }, [drawingId, retryCount]);

  const performSave = useCallback(async () => {
    if (saveStatus === 'saving' || loadStatus !== 'ready' || sessionRef.current === null) {
      return;
    }
    if (sessionRef.current.layoutVersionStatus === '잠금') {
      dispatch({
        type: 'setError',
        message: '시뮬레이션에 사용된 도면 버전은 잠겨 있어 수정할 수 없습니다.',
      });
      return;
    }
    setSaveStatus('saving');
    try {
      const version = await saveDrawing(drawingId, {
        ...sessionRef.current,
        doc: stateRef.current.doc,
      });
      sessionRef.current = {
        ...sessionRef.current,
        doc: stateRef.current.doc,
        version,
      };
      dispatch({ type: 'setValidationProblems', problems: [] });
      recordLastActivity('LAYOUT_EDIT', Number(drawingId));
      setSaveStatus('saved');
      if (saveTimerRef.current !== null) {
        window.clearTimeout(saveTimerRef.current);
      }
      saveTimerRef.current = window.setTimeout(() => {
        setSaveStatus('idle');
        saveTimerRef.current = null;
      }, 2000);
    } catch (error) {
      setSaveStatus('error');
      const conflict = error instanceof AxiosError && error.response?.status === 409;
      dispatch({
        type: 'setValidationProblems',
        problems: conflict
          ? []
          : parseValidationProblems(error instanceof AxiosError ? error.response?.data : undefined),
      });
      dispatch({
        type: 'setError',
        message: conflict
          ? '다른 사용자가 이 도면을 수정했습니다. 새로고침 후 다시 시도해 주세요.'
          : getDrawingErrorMessage(error),
      });
      if (saveTimerRef.current !== null) {
        window.clearTimeout(saveTimerRef.current);
      }
      saveTimerRef.current = window.setTimeout(() => {
        setSaveStatus('idle');
        saveTimerRef.current = null;
      }, 2000);
    }
  }, [saveStatus, loadStatus, drawingId, recordLastActivity]);

  const handleOpenDraftDialog = useCallback(async () => {
    const session = sessionRef.current;
    if (session === null || draftPending) return;
    const hasUnsavedChanges = JSON.stringify(stateRef.current.doc) !== JSON.stringify(session.doc);
    if (!hasUnsavedChanges || session.layoutVersionStatus === '잠금') {
      setDraftDialogOpen(true);
      return;
    }
    setDraftPending(true);
    try {
      const version = await saveDrawing(drawingId, { ...session, doc: stateRef.current.doc });
      sessionRef.current = { ...session, doc: stateRef.current.doc, version };
      recordLastActivity('LAYOUT_EDIT', Number(drawingId));
      setDraftDialogOpen(true);
    } catch (error) {
      dispatch({ type: 'setError', message: getSimulationErrorMessage(error) });
    } finally {
      setDraftPending(false);
    }
  }, [draftPending, drawingId, recordLastActivity]);

  const handleCreateDraft = useCallback(
    async (parentSimulationId?: number) => {
      const session = sessionRef.current;
      if (session === null || draftPending) return;
      setDraftPending(true);
      try {
        const draft = await simulationApi.createDraft({
          layoutVersionId: session.layoutVersionId,
          ...(parentSimulationId === undefined ? {} : { parentSimulationId }),
        });
        navigate(`/simulations/${draft.simulationId}/setup?defaultAllExits=true`);
      } catch (error) {
        setDraftDialogOpen(false);
        dispatch({
          type: 'setError',
          message: getSimulationErrorMessage(error),
        });
      } finally {
        setDraftPending(false);
      }
    },
    [draftPending, navigate],
  );

  useEffect(() => {
    if (state.error === null) {
      return;
    }
    const timer = window.setTimeout(() => {
      dispatch({ type: 'setError', message: null });
    }, 5000);
    return () => window.clearTimeout(timer);
  }, [state.error, state.errorNonce]);

  useEffect(() => {
    if (state.validationProblems.length === 0) {
      return;
    }
    const timer = window.setTimeout(() => {
      dispatch({ type: 'setValidationProblems', problems: [] });
    }, 8000);
    return () => window.clearTimeout(timer);
  }, [state.validationProblems]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')) {
        return;
      }
      const mod = event.metaKey || event.ctrlKey;
      const key = event.key.toLowerCase();
      const locked = sessionRef.current?.layoutVersionStatus === '잠금';
      if (
        locked &&
        ((mod && (key === 's' || key === 'z' || key === 'y')) ||
          event.key === 'Delete' ||
          event.key === 'Backspace')
      ) {
        event.preventDefault();
        return;
      }
      if (mod && key === 's') {
        event.preventDefault();
        void performSave();
      } else if (mod && key === 'z') {
        event.preventDefault();
        dispatch({ type: event.shiftKey ? 'redo' : 'undo' });
      } else if (mod && key === 'y') {
        event.preventDefault();
        dispatch({ type: 'redo' });
      } else if (event.key === 'Delete' || event.key === 'Backspace') {
        dispatch({ type: 'deleteSelection' });
      } else if (event.key === 'Escape') {
        dispatch({ type: 'escape' });
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [performSave]);

  const readOnly = sessionRef.current?.layoutVersionStatus === '잠금';
  const draftTextId = state.textDraft === null ? null : state.textDraft.textId;
  const draftInitialText =
    draftTextId === null
      ? ''
      : (state.doc.layoutTexts.find((t) => t.id === draftTextId)?.text ?? '');

  if (loadStatus === 'loading') {
    return <CanvasWorkspaceState message="도면 불러오는 중..." />;
  }

  if (loadStatus === 'missing') {
    return (
      <CanvasWorkspaceState
        message="도면을 찾을 수 없습니다."
        actions={
          <Button type="button" size="sm" onClick={() => navigate('/drawings')}>
            목록으로 이동
          </Button>
        }
      />
    );
  }

  if (loadStatus === 'error') {
    return (
      <CanvasWorkspaceState
        message="도면을 불러오지 못했습니다."
        actions={
          <>
            <Button type="button" size="sm" onClick={() => setRetryCount((count) => count + 1)}>
              다시 시도
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => navigate('/drawings')}
            >
              목록으로 이동
            </Button>
          </>
        }
      />
    );
  }

  return (
    <CanvasWorkspace className="layout-workspace">
      <CanvasWorkspaceBackButton onClick={() => navigate('/drawings')} />
      <LayoutWorkspaceHeader
        name={state.doc.name}
        readOnly={readOnly}
        onRename={(name) => dispatch({ type: 'renameDoc', name })}
      />
      <LayoutCanvas
        state={state}
        dispatch={dispatch}
        size={size}
        onSizeChange={onSizeChange}
        readOnly={readOnly}
      />
      {settingsPanel.isMinimized ? (
        <CanvasWorkspacePanelRestore
          ref={restoreButtonRef}
          aria-controls="layout-settings-panel"
          aria-expanded="false"
          onClick={() => {
            restorePanelFocusRef.current = true;
            settingsPanel.restore();
          }}
        >
          도면 설정 열기
        </CanvasWorkspacePanelRestore>
      ) : (
        <CanvasWorkspacePanel
          id="layout-settings-panel"
          ariaLabel="도면 설정"
          animate
          className={`${settingsPanel.isCollapsing ? 'is-collapsing' : ''} ${settingsPanel.isExpanding ? 'is-expanding' : ''}`}
          onAnimationEnd={(event) => {
            if (event.currentTarget === event.target) settingsPanel.handleAnimationEnd();
          }}
        >
          <LayoutToolbar
            saveStatus={saveStatus}
            onSave={() => void performSave()}
            onStartSimulation={() => void handleOpenDraftDialog()}
            readOnly={readOnly}
            collapseButtonRef={collapseButtonRef}
            onCollapse={() => {
              restorePanelFocusRef.current = true;
              settingsPanel.collapse();
            }}
          />
          <div className="min-h-0 flex-1 overflow-y-auto">
            <div className={readOnly ? 'pointer-events-none opacity-60' : ''}>
              <SettingsPanel state={state} dispatch={dispatch} />
            </div>
          </div>
        </CanvasWorkspacePanel>
      )}
      <ToolToolbar
        state={state}
        dispatch={dispatch}
        disabled={readOnly}
        className="layout-workspace-tool-dock"
      />
      <ZoomControl
        state={state}
        dispatch={dispatch}
        size={size}
        className="layout-workspace-zoom"
      />
      {state.textDraft && (
        <InlineTextInput
          key={`${state.textDraft.point.x}:${state.textDraft.point.y}`}
          point={state.textDraft.point}
          zoom={state.camera.zoom}
          panX={state.camera.panX}
          panY={state.camera.panY}
          initialText={draftInitialText}
          onCommit={(text) => dispatch({ type: 'textCommit', text })}
          onCancel={() => dispatch({ type: 'textCancel' })}
        />
      )}
      {state.error && (
        <div
          role="alert"
          className="layout-workspace-alert flex max-w-[320px] items-center gap-2 rounded-md border border-danger bg-surface-overlay px-3 py-2 text-sm text-danger shadow-neu-floating"
        >
          <span className="min-w-0">{state.error}</span>
          <button
            type="button"
            onClick={() => dispatch({ type: 'setError', message: null })}
            aria-label="닫기"
            className="shrink-0 text-danger transition-colors hover:opacity-70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
          >
            ×
          </button>
        </div>
      )}
      {draftDialogOpen && sessionRef.current !== null && (
        <CreateSimulationDraftDialog
          layoutVersionId={sessionRef.current.layoutVersionId}
          pending={draftPending}
          onClose={() => setDraftDialogOpen(false)}
          onConfirm={(parentSimulationId) => void handleCreateDraft(parentSimulationId)}
        />
      )}
    </CanvasWorkspace>
  );
}

export default LayoutPage;
