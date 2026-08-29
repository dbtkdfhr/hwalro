import { useCallback, useEffect, useLayoutEffect, useReducer, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { Minus } from 'lucide-react';
import { isCancelledRequest, loadWithRetry } from '../../../api/loadWithRetry';
import { useDelayedLoadingMessage } from '../../../hooks/useDelayedLoadingMessage';
import { DRAWING_WORKSPACE_LOADING_MESSAGE } from '../../../components/workspace/workspaceLoadingMessages';
import { LayoutCanvas } from '../components/LayoutCanvas';
import { LayoutPrimaryActions, LayoutToolbar } from '../components/LayoutToolbar';
import { LayoutWorkspaceHeader } from '../components/LayoutWorkspaceHeader';
import { ToolToolbar } from '../components/ToolToolbar';
import { ZoomControl } from '../components/ZoomControl';
import { SettingsPanel } from '../components/SettingsPanel';
import { LayersPanel } from '../components/LayersPanel';
import { ZonePanel, StructureConstraintPanel } from '../components/ZonePanel';
import { VersionHistoryDialog } from '../components/VersionHistoryDialog';
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
import type { Drawing } from '../../drawings/types/drawing';
import type { DrawingDocument, ValidationProblem, ValidationProblemKind, Vec2 } from '../types';
import { CreateSimulationDraftDialog } from '../../simulations/components/CreateSimulationDraftDialog';
import { simulationApi } from '../../simulations/api/simulationApi';
import { getSimulationErrorMessage } from '../../simulations/utils/getSimulationErrorMessage';
import { getDrawingErrorMessage } from '../../drawings/utils/getDrawingErrorMessage';
import { useRecordLastActivity } from '../../home/hooks/useRecordLastActivity';
import { useLayoutMetadata } from '../hooks/useLayoutMetadata';
import { useZoneRectHistory } from '../hooks/useZoneRectHistory';
import { zoneOfFabric } from '../utils/zoneMembership';
import type { LayerElement } from '../utils/zoneMembership';
import { boundingBoxOf } from '../utils/zoneGeometry';
import { canEditStructureConstraints } from '../utils/structureConstraintPolicy';
import { buildLayerMenu, canGroupSelection } from '../utils/layerMenu';
import { LayerContextMenu } from '../components/LayerContextMenu';
import type { ElementHit } from '../utils/hitTest';
import { centerCameraOnPoint } from '../utils/geometry';
import { reorderRelative, type DropPosition } from '../utils/layerDrop';
import { authApi } from '../../auth/api/authApi';
import { useAuth } from '../../auth/context/AuthContext';
import { can } from '../../auth/capabilities';
import type { EmployeeSummary } from '../../auth/types/auth';
import type { ZoneRect, ZoneType } from '../api/layoutMetadataApi';
import { riskApi } from '../../risks/api/riskApi';
import type { Risk } from '../../risks/types/risks';
import { RiskZoneEditorDialog } from '../../risks/components/RiskZoneEditorDialog';
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

function hasUnsavedDocChanges(stateDoc: DrawingDocument, sessionDoc: DrawingDocument): boolean {
  return JSON.stringify(stateDoc) !== JSON.stringify(sessionDoc);
}

function parseLayoutId(value: string | undefined): number | null {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

interface LayoutNavigationState {
  fadeInLayout?: boolean;
  returnTo?: string;
  returnLabel?: string;
}

function readResultReturnPath(state: LayoutNavigationState | null): string | null {
  return typeof state?.returnTo === 'string' && /^\/simulations\/\d+\/results$/.test(state.returnTo)
    ? state.returnTo
    : null;
}

function LayoutPage() {
  const { drawingId = '' } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const recordLastActivity = useRecordLastActivity();
  const layoutId = parseLayoutId(drawingId);
  const [state, dispatch] = useReducer(editorReducer, undefined, createInitialState);
  const [loadStatus, setLoadStatus] = useState<LoadStatus>('loading');
  const loadingMessage = useDelayedLoadingMessage(
    loadStatus === 'loading',
    DRAWING_WORKSPACE_LOADING_MESSAGE,
  );
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle');
  const [size, setSize] = useState({ w: 0, h: 0 });
  const [retryCount, setRetryCount] = useState(0);
  const settingsPanel = useCollapsibleWorkspacePanel();
  const layersPanel = useCollapsibleWorkspacePanel();
  const { user } = useAuth();
  const canManageZones = can(user?.roles, 'zones.manage');
  const canManageGeometry = can(user?.roles, 'drawings.manage');
  const canManageRisks = can(user?.roles, 'risks');
  const metadata = useLayoutMetadata(drawingId);
  const persistZoneRect = useCallback(
    (zoneId: number, rect: ZoneRect) =>
      metadata.updateZone(
        zoneId,
        { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
        (zone) => ({ ...zone, rect }),
      ),
    [metadata.updateZone],
  );
  const zoneRectHistory = useZoneRectHistory(drawingId, persistZoneRect);
  const [selectedZoneId, setSelectedZoneId] = useState<number | null>(null);
  const [canvasMenu, setCanvasMenu] = useState<{ anchor: Vec2; element: LayerElement } | null>(
    null,
  );
  const [employees, setEmployees] = useState<EmployeeSummary[]>([]);
  const [draftDialogOpen, setDraftDialogOpen] = useState(false);
  const [draftPending, setDraftPending] = useState(false);
  const [historyDialogOpen, setHistoryDialogOpen] = useState(false);
  const navigationState = location.state as LayoutNavigationState | null;
  const fadeInLayout = navigationState?.fadeInLayout === true;
  const resultReturnPath = readResultReturnPath(navigationState);
  const backLabel =
    resultReturnPath && typeof navigationState?.returnLabel === 'string'
      ? navigationState.returnLabel
      : undefined;
  const [riskMode, setRiskMode] = useState(false);
  const [risks, setRisks] = useState<Risk[]>([]);
  const [pendingRiskBounds, setPendingRiskBounds] = useState<{
    x: number;
    y: number;
    width: number;
    height: number;
  } | null>(null);
  const stateRef = useRef(state);
  const sessionRef = useRef<DrawingSession | null>(null);
  const riskModeRef = useRef(false);
  const loadedRef = useRef(false);
  const saveTimerRef = useRef<number | null>(null);
  const collapseButtonRef = useRef<HTMLButtonElement>(null);
  const restoreButtonRef = useRef<HTMLButtonElement>(null);
  const restorePanelFocusRef = useRef(false);
  const layersCollapseButtonRef = useRef<HTMLButtonElement>(null);
  const layersRestoreButtonRef = useRef<HTMLButtonElement>(null);
  const restoreLayersFocusRef = useRef(false);

  useLayoutEffect(() => {
    stateRef.current = state;
  }, [state]);

  const toggleRiskMode = useCallback(() => {
    setRiskMode((current) => {
      riskModeRef.current = !current;
      return !current;
    });
  }, []);

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

  useLayoutEffect(() => {
    if (!restoreLayersFocusRef.current) return;
    if (layersPanel.isMinimized) {
      layersRestoreButtonRef.current?.focus();
      restoreLayersFocusRef.current = false;
    } else if (layersPanel.isExpanding) {
      layersCollapseButtonRef.current?.focus();
      restoreLayersFocusRef.current = false;
    }
  }, [layersPanel.isExpanding, layersPanel.isMinimized]);

  useEffect(() => {
    if (!canManageZones) {
      return;
    }
    let active = true;
    // 이름을 못 붙여도 편집기는 계속 동작해야 하므로 실패를 삼킨다.
    authApi
      .employees()
      .then((list) => {
        if (active) setEmployees(list);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [canManageZones]);

  const onSizeChange = useCallback((next: { w: number; h: number }) => {
    setSize(next);
  }, []);

  useEffect(() => {
    if (loadedRef.current) {
      return;
    }
    loadedRef.current = true;
    const controller = new AbortController();
    sessionRef.current = null;
    setLoadStatus('loading');
    loadWithRetry(() => fetchDrawing(drawingId, controller.signal), {
      signal: controller.signal,
    })
      .then((session) => {
        if (controller.signal.aborted) {
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
      .catch((error: unknown) => {
        if (!controller.signal.aborted && !isCancelledRequest(error)) {
          setLoadStatus('error');
        }
      });
    return () => {
      controller.abort();
      loadedRef.current = false;
    };
  }, [drawingId, retryCount]);

  /**
   * 저장 결과를 편집기에 반영한다.
   *
   * 저장은 새 도면 버전에 요소를 새 ID로 다시 만든다. 요소의 서버 ID를 갱신하고 구역 메타데이터도
   * 다시 읽어야 한다. 둘 중 하나만 하면 구역과 요소가 서로 다른 버전의 ID를 가리켜 소속이 통째로
   * 사라진 것처럼 보인다.
   */
  // 저장은 구역도 새 ID로 다시 만든다. 사라진 구역을 계속 선택해 두면 우측 패널이 빈 채로 남는다.
  useEffect(() => {
    if (selectedZoneId === null) return;
    if (!metadata.metadata.zones.some((zone) => zone.zoneId === selectedZoneId)) {
      setSelectedZoneId(null);
    }
  }, [metadata.metadata.zones, selectedZoneId]);

  useEffect(() => {
    if (loadStatus !== 'ready' || layoutId === null || !canManageRisks) {
      return;
    }
    let active = true;
    riskApi
      .listByLayout(layoutId)
      .then((items) => {
        if (active) setRisks(items);
      })
      .catch(() => {
        if (active) setRisks([]);
      });
    return () => {
      active = false;
    };
  }, [canManageRisks, layoutId, loadStatus]);

  const adoptSavedDrawing = useCallback(
    (drawing: Drawing) => {
      dispatch({
        type: 'adoptSavedIds',
        walls: drawing.walls.map((wall) => wall.id ?? null),
        exits: drawing.exits.map((exit) => exit.id ?? null),
        pillars: drawing.pillars.map((pillar) => pillar.id ?? null),
        fabrics: drawing.fabrics.map((fabric) => fabric.id ?? null),
      });
      void metadata.reload();
    },
    [metadata.reload],
  );

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
      const drawing = await saveDrawing(drawingId, {
        ...sessionRef.current,
        doc: stateRef.current.doc,
      });
      adoptSavedDrawing(drawing);
      sessionRef.current = {
        ...sessionRef.current,
        doc: stateRef.current.doc,
        version: drawing.version,
        layoutVersionId: drawing.layoutVersionId,
        layoutVersionNumber: drawing.layoutVersionNumber,
        layoutVersionStatus: drawing.layoutVersionStatus,
      };
      dispatch({ type: 'setValidationProblems', problems: [] });
      if (layoutId !== null) {
        recordLastActivity('LAYOUT_EDIT', layoutId);
      }
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
  }, [saveStatus, loadStatus, drawingId, layoutId, recordLastActivity]);

  const handleOpenDraftDialog = useCallback(async () => {
    const session = sessionRef.current;
    if (session === null || draftPending) return;
    const hasUnsavedChanges = hasUnsavedDocChanges(stateRef.current.doc, session.doc);
    if (!hasUnsavedChanges || session.layoutVersionStatus === '잠금') {
      setDraftDialogOpen(true);
      return;
    }
    setDraftPending(true);
    try {
      const drawing = await saveDrawing(drawingId, { ...session, doc: stateRef.current.doc });
      adoptSavedDrawing(drawing);
      sessionRef.current = {
        ...session,
        doc: stateRef.current.doc,
        version: drawing.version,
        layoutVersionId: drawing.layoutVersionId,
        layoutVersionNumber: drawing.layoutVersionNumber,
        layoutVersionStatus: drawing.layoutVersionStatus,
      };
      if (layoutId !== null) {
        recordLastActivity('LAYOUT_EDIT', layoutId);
      }
      setDraftDialogOpen(true);
    } catch (error) {
      dispatch({ type: 'setError', message: getSimulationErrorMessage(error) });
    } finally {
      setDraftPending(false);
    }
  }, [draftPending, drawingId, layoutId, recordLastActivity]);

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
      if (mod && (key === 'z' || key === 'y')) {
        event.preventDefault();
        if (event.repeat || zoneRectHistory.pending) return;
        const redoRequested = key === 'y' || event.shiftKey;
        if (selectedZoneId !== null) {
          if (redoRequested && zoneRectHistory.canRedo) {
            void zoneRectHistory.redo();
            return;
          }
          if (!redoRequested && zoneRectHistory.canUndo) {
            void zoneRectHistory.undo();
            return;
          }
        }
        if (!locked) dispatch({ type: redoRequested ? 'redo' : 'undo' });
        return;
      }
      if (locked && ((mod && key === 's') || event.key === 'Delete' || event.key === 'Backspace')) {
        event.preventDefault();
        return;
      }
      if (mod && key === 's') {
        event.preventDefault();
        void performSave();
      } else if (event.key === 'Delete' || event.key === 'Backspace') {
        dispatch({ type: 'deleteSelection' });
      } else if (event.key === 'Escape') {
        if (riskModeRef.current) {
          setRiskMode(false);
          riskModeRef.current = false;
        }
        dispatch({ type: 'escape' });
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [performSave, selectedZoneId, zoneRectHistory]);

  const handleRestored = useCallback((session: DrawingSession) => {
    sessionRef.current = session;
    dispatch({ type: 'loadDocument', doc: session.doc });
    setSaveStatus('idle');
    setHistoryDialogOpen(false);
  }, []);

  const handleUpdateDrawingInfo = useCallback(
    (info: { title: string; description: string | null }) => {
      const nextDescription = info.description === '' ? null : info.description;
      if (sessionRef.current !== null && sessionRef.current.description !== nextDescription) {
        sessionRef.current = { ...sessionRef.current, description: nextDescription };
      }
      if (stateRef.current.doc.name !== info.title) {
        dispatch({ type: 'renameDoc', name: info.title });
      }
    },
    [],
  );
  useEffect(() => {
    if (loadStatus !== 'ready' || layoutId === null) {
      return;
    }
    let active = true;
    riskApi
      .listByLayout(layoutId)
      .then((items) => {
        if (active) setRisks(items);
      })
      .catch(() => {
        if (active) setRisks([]);
      });
    return () => {
      active = false;
    };
  }, [loadStatus, layoutId]);

  const readOnly = sessionRef.current?.layoutVersionStatus === '잠금';
  const employeeNameById = Object.fromEntries(
    employees.map((employee) => [employee.id, employee.name]),
  );
  const selectedZone =
    metadata.metadata.zones.find((zone) => zone.zoneId === selectedZoneId) ?? null;
  const selectedFabric =
    state.doc.fabrics.find((fabric) => fabric.id === state.selection.fabricIds[0]) ?? null;
  const selectedFabricZone =
    selectedFabric === null ? null : zoneOfFabric(selectedFabric, metadata.metadata.zones);
  const selectedFabricConstraint =
    selectedFabric?.backendId == null
      ? null
      : (metadata.metadata.structureConstraints.find(
          (constraint) => constraint.fabricId === selectedFabric.backendId,
        ) ?? null);
  const canEditSelectedConstraints = canEditStructureConstraints(
    user?.roles,
    user?.id ?? null,
    selectedFabricZone,
  );

  const handleZoneDrawn = (rect: ZoneRect) => {
    if (rect.width <= 0 || rect.height <= 0) {
      return;
    }
    void metadata
      .createZone({
        ...rect,
        name: `구역 ${metadata.metadata.zones.length + 1}`,
        zoneType: 'WORK',
        assignedUserId: null,
        defaultExitId: null,
        members: null,
      })
      .then((created) => {
        if (created) {
          setSelectedZoneId(created.zoneId);
          dispatch({ type: 'setTool', tool: 'select' });
        }
      });
  };

  const clearElementSelection = () =>
    dispatch({
      type: 'selectAt',
      wallId: null,
      outsideWallId: null,
      exitId: null,
      textId: null,
      pillarId: null,
      fabricId: null,
      additive: false,
    });

  const handleSelectZone = (zoneId: number | null) => {
    setSelectedZoneId(zoneId);
    if (zoneId !== null) {
      clearElementSelection();
    }
  };

  const handleChangeMembership = (element: LayerElement, targetZoneId: number | null) => {
    const { doc, selection } = stateRef.current;
    const selectedElements: LayerElement[] = [
      ...doc.walls
        .filter((wall) => selection.wallIds.includes(wall.id))
        .map((wall) => ({ ...wall, kind: 'WALL' as const })),
      ...doc.pillars
        .filter((pillar) => selection.pillarIds.includes(pillar.id))
        .map((pillar) => ({ ...pillar, kind: 'PILLAR' as const })),
      ...doc.fabrics
        .filter((fabric) => selection.fabricIds.includes(fabric.id))
        .map((fabric) => ({ ...fabric, kind: 'FABRIC' as const })),
    ];
    const movingElements = selectedElements.some(
      (selected) => selected.kind === element.kind && selected.id === element.id,
    )
      ? selectedElements
      : [element];
    if (movingElements.some((moving) => moving.backendId === null)) {
      dispatch({ type: 'setError', message: '도면을 저장한 뒤 구역으로 이동해 주세요.' });
      return;
    }
    const zones = metadata.metadata.zones;
    const isMovingMember = (member: { kind: string; id: number }) =>
      movingElements.some(
        (moving) => moving.kind === member.kind && moving.backendId === member.id,
      );
    const target = zones.find((zone) => zone.zoneId === targetZoneId);
    if (targetZoneId !== null && !target) return;

    // 한 요소는 한 구역에만 속한다(DB UNIQUE). 원래 구역들에서 먼저 빼고 대상 구역에는 한 번만 추가한다.
    void (async () => {
      for (const source of zones.filter(
        (zone) => zone.zoneId !== targetZoneId && zone.members.some(isMovingMember),
      )) {
        const sourceMembers = source.members.filter((member) => !isMovingMember(member));
        const removed = await metadata.updateZone(
          source.zoneId,
          { members: sourceMembers },
          (zone) => ({ ...zone, members: sourceMembers }),
        );
        if (!removed) return;
      }
      if (target) {
        const targetMembers = [
          ...target.members.filter((member) => !isMovingMember(member)),
          ...movingElements.map((moving) => ({
            kind: moving.kind,
            id: moving.backendId as number,
          })),
        ];
        await metadata.updateZone(target.zoneId, { members: targetMembers }, (zone) => ({
          ...zone,
          members: targetMembers,
        }));
      }
    })();
  };

  const handleMoveZoneOrder = (
    draggedZoneId: number,
    targetZoneId: number,
    position: DropPosition,
  ) => {
    const ordered = [...metadata.metadata.zones].sort(
      (left, right) => left.displayOrder - right.displayOrder,
    );
    const reordered = reorderRelative(
      ordered,
      String(draggedZoneId),
      String(targetZoneId),
      position,
      (zone) => String(zone.zoneId),
    );
    if (reordered === ordered) return;
    const displayOrders = ordered.map((zone) => zone.displayOrder);
    void (async () => {
      for (const [index, zone] of reordered.entries()) {
        const displayOrder = displayOrders[index];
        if (zone.displayOrder !== displayOrder) {
          await metadata.updateZone(zone.zoneId, { displayOrder }, (current) => ({
            ...current,
            displayOrder,
          }));
        }
      }
    })();
  };

  /** 캔버스에서 우클릭한 요소. 계층 패널과 같은 메뉴를 포인터 위치에 띄운다. */
  const handleCanvasContextMenu = (anchor: Vec2, hit: ElementHit) => {
    const { doc } = stateRef.current;
    const found =
      (hit.wallId !== null &&
        doc.walls
          .filter((wall) => wall.id === hit.wallId)
          .map((wall) => ({ kind: 'WALL' as const, element: wall }))[0]) ||
      (hit.pillarId !== null &&
        doc.pillars
          .filter((pillar) => pillar.id === hit.pillarId)
          .map((pillar) => ({ kind: 'PILLAR' as const, element: pillar }))[0]) ||
      (hit.fabricId !== null &&
        doc.fabrics
          .filter((fabric) => fabric.id === hit.fabricId)
          .map((fabric) => ({ kind: 'FABRIC' as const, element: fabric }))[0]);
    if (!found) return;
    setCanvasMenu({
      anchor,
      element: {
        kind: found.kind,
        id: found.element.id,
        backendId: found.element.backendId,
        name: found.element.name,
      },
    });
  };

  const handleGroupSelectionIntoZone = () => {
    const { doc, selection } = stateRef.current;
    const walls = doc.walls.filter((wall) => selection.wallIds.includes(wall.id));
    const pillars = doc.pillars.filter((pillar) => selection.pillarIds.includes(pillar.id));
    const fabrics = doc.fabrics.filter((fabric) => selection.fabricIds.includes(fabric.id));
    const chosen = [...walls, ...pillars, ...fabrics];
    if (chosen.length === 0 || !chosen.every((element) => element.backendId !== null)) {
      return;
    }
    const box = boundingBoxOf(walls, pillars, fabrics, doc.width, doc.height);
    if (box === null || box.width <= 0 || box.height <= 0) {
      return;
    }
    const members = [
      ...walls.map((wall) => ({ kind: 'WALL' as const, id: wall.backendId! })),
      ...pillars.map((pillar) => ({ kind: 'PILLAR' as const, id: pillar.backendId! })),
      ...fabrics.map((fabric) => ({ kind: 'FABRIC' as const, id: fabric.backendId! })),
    ];
    void metadata
      .createZone({
        ...box,
        name: `구역 ${metadata.metadata.zones.length + 1}`,
        zoneType: 'WORK',
        assignedUserId: null,
        defaultExitId: null,
        members,
      })
      .then((created) => {
        if (created) {
          setSelectedZoneId(created.zoneId);
          clearElementSelection();
        }
      });
  };

  const handleZoneRectCommit = (zoneId: number, previousRect: ZoneRect, nextRect: ZoneRect) => {
    void zoneRectHistory.commit({ zoneId, before: previousRect, after: nextRect });
  };

  const handleCenterPoint = (point: { x: number; y: number }) => {
    if (size.w <= 0 || size.h <= 0) return;
    dispatch({
      type: 'setCamera',
      camera: centerCameraOnPoint(
        state.camera,
        point,
        state.doc.width,
        state.doc.height,
        size.w,
        size.h,
      ),
    });
  };

  const draftTextId = state.textDraft === null ? null : state.textDraft.textId;
  const draftInitialText =
    draftTextId === null
      ? ''
      : (state.doc.layoutTexts.find((t) => t.id === draftTextId)?.text ?? '');

  if (loadStatus === 'loading') {
    return <CanvasWorkspaceState message={loadingMessage} />;
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
    <CanvasWorkspace
      className={`layout-workspace ${fadeInLayout ? 'layout-workspace--entering' : ''}`}
    >
      <CanvasWorkspaceBackButton
        label={backLabel}
        onClick={() => navigate(resultReturnPath ?? '/drawings')}
      />
      <LayoutWorkspaceHeader
        name={state.doc.name}
        description={sessionRef.current?.description ?? null}
        readOnly={readOnly}
        onUpdateInfo={handleUpdateDrawingInfo}
      />
      <LayoutCanvas
        state={state}
        dispatch={dispatch}
        size={size}
        onSizeChange={onSizeChange}
        readOnly={readOnly}
        geometryEditable={!readOnly && canManageGeometry}
        zones={metadata.metadata.zones}
        selectedZoneId={selectedZoneId}
        onZoneDrawn={handleZoneDrawn}
        onSelectZone={setSelectedZoneId}
        onZoneRectCommit={handleZoneRectCommit}
        canEditZones={canManageZones}
        onElementContextMenu={canManageZones ? handleCanvasContextMenu : undefined}
        riskZones={risks.map((risk) => ({
          id: risk.id,
          title: risk.title,
          startX: risk.startX ?? 0,
          startY: risk.startY ?? 0,
          endX: risk.endX ?? 0,
          endY: risk.endY ?? 0,
        }))}
        riskMode={canManageRisks && riskMode}
        onRiskZoneDrawn={canManageRisks ? setPendingRiskBounds : undefined}
      />
      {canvasMenu !== null ? (
        <LayerContextMenu
          anchor={canvasMenu.anchor}
          items={buildLayerMenu({
            element: canvasMenu.element,
            zones: metadata.metadata.zones,
            canGroupSelected: canGroupSelection(stateRef.current),
            onChangeMembership: handleChangeMembership,
            onGroupSelectionIntoZone: handleGroupSelectionIntoZone,
          })}
          onClose={() => setCanvasMenu(null)}
        />
      ) : null}
      {layersPanel.isMinimized ? (
        <CanvasWorkspacePanelRestore
          ref={layersRestoreButtonRef}
          aria-controls="layout-layers-panel"
          aria-expanded="false"
          onClick={() => {
            restoreLayersFocusRef.current = true;
            layersPanel.restore();
          }}
          className="layout-workspace-layers-restore"
        >
          계층 열기
        </CanvasWorkspacePanelRestore>
      ) : (
        <CanvasWorkspacePanel
          id="layout-layers-panel"
          ariaLabel="도면 계층"
          animate
          className={`layout-workspace-layers ${layersPanel.isCollapsing ? 'is-collapsing' : ''} ${layersPanel.isExpanding ? 'is-expanding' : ''}`}
          onAnimationEnd={(event) => {
            if (event.currentTarget === event.target) layersPanel.handleAnimationEnd();
          }}
        >
          <div className="flex items-center justify-between border-b border-panel-divider px-3 py-2">
            <h2 className="text-sm font-bold text-panel-text">계층</h2>
            <button
              ref={layersCollapseButtonRef}
              type="button"
              aria-controls="layout-layers-panel"
              aria-expanded="true"
              aria-label="도면 계층 최소화"
              onClick={() => {
                restoreLayersFocusRef.current = true;
                layersPanel.collapse();
              }}
              className="layout-panel-actions__collapse"
            >
              <Minus aria-hidden="true" />
            </button>
          </div>
          <LayersPanel
            state={state}
            dispatch={dispatch}
            zones={metadata.metadata.zones}
            selectedZoneId={selectedZoneId}
            onSelectZone={handleSelectZone}
            employeeNameById={employeeNameById}
            orderLocked={readOnly}
            membershipEditable={canManageZones}
            onChangeMembership={handleChangeMembership}
            onGroupSelectionIntoZone={handleGroupSelectionIntoZone}
            onMoveZoneOrder={handleMoveZoneOrder}
            onCenterPoint={handleCenterPoint}
          />
        </CanvasWorkspacePanel>
      )}
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
            onOpenHistory={() => setHistoryDialogOpen(true)}
            readOnly={readOnly || !canManageGeometry}
            riskMode={riskMode}
            onToggleRiskMode={canManageRisks ? toggleRiskMode : undefined}
            collapseButtonRef={collapseButtonRef}
            onCollapse={() => {
              restorePanelFocusRef.current = true;
              settingsPanel.collapse();
            }}
          />
          <div className="min-h-0 flex-1 overflow-y-auto">
            {metadata.errorMessage ? (
              <p
                role="alert"
                className="mx-3 mt-3 rounded-md border border-danger/40 bg-panel-soft px-3 py-2 text-xs text-danger"
              >
                {metadata.errorMessage}
              </p>
            ) : null}
            {selectedZone !== null ? (
              <div className="space-y-3 px-3 py-3">
                <ZonePanel
                  zone={selectedZone}
                  exits={state.doc.exits}
                  employees={employees}
                  readOnly={!canManageZones}
                  onRename={(name) =>
                    void metadata.updateZone(selectedZone.zoneId, { name }, (zone) => ({
                      ...zone,
                      name,
                    }))
                  }
                  onChangeType={(zoneType: ZoneType) =>
                    void metadata.updateZone(selectedZone.zoneId, { zoneType }, (zone) => ({
                      ...zone,
                      zoneType,
                    }))
                  }
                  onChangeRect={(patch) =>
                    void zoneRectHistory.commit({
                      zoneId: selectedZone.zoneId,
                      before: selectedZone.rect,
                      after: { ...selectedZone.rect, ...patch },
                    })
                  }
                  onAssign={(assignedUserId) =>
                    void metadata.updateZone(
                      selectedZone.zoneId,
                      assignedUserId === null ? { clearAssignedUser: true } : { assignedUserId },
                      (zone) => ({ ...zone, assignedUserId }),
                    )
                  }
                  onChangeExit={(exitId) =>
                    void metadata.updateZone(
                      selectedZone.zoneId,
                      exitId === null ? { clearDefaultExit: true } : { defaultExitId: exitId },
                      (zone) => ({ ...zone, defaultExitId: exitId }),
                    )
                  }
                  onDelete={() => {
                    void metadata.deleteZone(selectedZone.zoneId);
                    setSelectedZoneId(null);
                  }}
                />
              </div>
            ) : (
              <>
                <div
                  className={readOnly || !canManageGeometry ? 'pointer-events-none opacity-60' : ''}
                >
                  <SettingsPanel state={state} dispatch={dispatch} />
                </div>
                {selectedFabric !== null ? (
                  <div className="px-3 pb-4">
                    <StructureConstraintPanel
                      fabricName={selectedFabric.name}
                      zoneName={selectedFabricZone?.name ?? null}
                      constraint={selectedFabricConstraint}
                      editable={canEditSelectedConstraints}
                      saved={selectedFabric.backendId !== null}
                      onChange={(patch) => {
                        if (selectedFabric.backendId === null) {
                          return;
                        }
                        void metadata.updateStructureConstraints(
                          selectedFabric.backendId,
                          patch,
                          patch,
                        );
                      }}
                    />
                  </div>
                ) : null}
              </>
            )}
            <LayoutPrimaryActions
              saveStatus={saveStatus}
              onSave={() => void performSave()}
              onStartSimulation={() => void handleOpenDraftDialog()}
              readOnly={readOnly || !canManageGeometry}
            />
          </div>
        </CanvasWorkspacePanel>
      )}
      <ToolToolbar
        state={state}
        dispatch={dispatch}
        disabled={readOnly || !canManageGeometry}
        zoneDisabled={!canManageZones}
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
          className="absolute bottom-4 right-4 z-20 flex max-w-[320px] items-center gap-2 rounded-md border border-danger bg-white px-3 py-2 text-sm text-danger shadow-raised"
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
      {pendingRiskBounds && layoutId !== null && sessionRef.current !== null && (
        <RiskZoneEditorDialog
          bounds={pendingRiskBounds}
          drawing={{
            width: state.doc.width,
            height: state.doc.height,
            layoutTexts: state.doc.layoutTexts.map((text) => ({
              text: text.text,
              x: text.x,
              y: text.y,
            })),
            zones: metadata.metadata.zones.map((zone) => ({
              name: zone.name,
              rect: zone.rect,
            })),
          }}
          layoutId={layoutId}
          layoutVersionId={sessionRef.current.layoutVersionId}
          onCancel={() => setPendingRiskBounds(null)}
          onConfirm={(risk) => {
            setRisks((current) => [risk, ...current]);
            setPendingRiskBounds(null);
            setRiskMode(false);
            riskModeRef.current = false;
          }}
        />
      )}
      {historyDialogOpen && sessionRef.current !== null && (
        <VersionHistoryDialog
          drawingId={drawingId}
          currentVersionId={sessionRef.current.layoutVersionId}
          locked={readOnly}
          hasUnsavedChanges={hasUnsavedDocChanges(stateRef.current.doc, sessionRef.current.doc)}
          onClose={() => setHistoryDialogOpen(false)}
          onRestored={handleRestored}
        />
      )}
    </CanvasWorkspace>
  );
}

export default LayoutPage;
