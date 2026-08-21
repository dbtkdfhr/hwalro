import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Info, Minus, MousePointer2, Plus, X } from 'lucide-react';
import {
  HAZARD_MAX_RADIUS,
  HAZARD_MIN_RADIUS,
  SimulationCanvas,
  type SimulationTool,
} from '../components/SimulationCanvas';
import {
  AgentDeletionConfirmDialog,
  AgentDeletionSuccessToast,
} from '../components/AgentDeletionFeedback';
import { NumberStepperInput } from '../components/NumberStepperInput';
import { simulationApi } from '../api/simulationApi';
import type { EditableHazardZone, SimulationPoint, SimulationSetup } from '../types';
import {
  AGENT_RADIUS,
  MAX_AGENTS,
  addSprayedAgents,
  createUniformPlacement,
  eraseAgents,
  parseHighlightedAgentId,
} from '../utils/placement';
import { getSimulationErrorMessage } from '../utils/getSimulationErrorMessage';
import { useRecordLastActivity } from '../../home/hooks/useRecordLastActivity';
import { Button } from '../../../components/ui';
import {
  CanvasWorkspace,
  CanvasWorkspaceBackButton,
  CanvasWorkspaceHeader,
  CanvasWorkspacePanel,
  CanvasWorkspacePanelRestore,
  CanvasWorkspaceState,
  useCollapsibleWorkspacePanel,
} from '../../../components/workspace';
import '../simulationSetup.css';

interface PlacementSnapshot {
  agents: SimulationPoint[];
  hazards: EditableHazardZone[];
}

type LoadState = 'loading' | 'ready' | 'error';
type SaveState = 'idle' | 'saving' | 'saved' | 'error';
type ExecutionPhase = 'idle' | 'saving' | 'validating' | 'requesting';
type PageAlert = { tone: 'error' | 'success'; text: string } | null;
type AgentDeletionToast = { state: 'confirm' | 'success'; count: number } | null;

interface InfoTooltipProps {
  id: string;
  label: string;
  align?: 'left' | 'right';
  children: ReactNode;
}

const TOOL_LABELS: Array<{ value: SimulationTool; label: string }> = [
  { value: 'select', label: '선택' },
  { value: 'spray', label: '에이전트 배치' },
  { value: 'erase', label: '지우개' },
  { value: 'hazard', label: '위험구역' },
];

function InfoTooltip({ id, label, align = 'left', children }: InfoTooltipProps) {
  return (
    <span className="group relative inline-flex">
      <button
        type="button"
        aria-label={label}
        aria-describedby={id}
        className="flex h-4 w-4 items-center justify-center rounded-full border border-current text-text-faint outline-none transition focus-visible:ring-2 focus-visible:ring-focus-ring"
      >
        <Info aria-hidden="true" className="h-3 w-3" />
      </button>
      <span
        id={id}
        role="tooltip"
        className={`simulation-setup-info-tooltip pointer-events-none absolute top-full z-30 mt-2 hidden w-48 rounded-lg px-3 py-2 text-[11px] font-medium leading-5 shadow-raised group-hover:block group-focus-within:block ${align === 'right' ? 'right-0' : 'left-0'}`}
      >
        {children}
      </span>
    </span>
  );
}

function sameSnapshot(a: PlacementSnapshot, b: PlacementSnapshot): boolean {
  return a.agents === b.agents && a.hazards === b.hazards;
}

function errorAlert(text: string): PageAlert {
  return { tone: 'error', text };
}

function SimulationSetupPage() {
  const { simulationId = '' } = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const recordLastActivity = useRecordLastActivity();
  const requestedDefaultAllExitsRef = useRef({
    simulationId,
    value: searchParams.get('defaultAllExits') === 'true',
  });
  if (requestedDefaultAllExitsRef.current.simulationId !== simulationId) {
    requestedDefaultAllExitsRef.current = {
      simulationId,
      value: searchParams.get('defaultAllExits') === 'true',
    };
  }
  const settingsPanel = useCollapsibleWorkspacePanel();
  const requestedHighlightRef = useRef({
    simulationId,
    value: searchParams.get('highlightAgent'),
  });
  if (requestedHighlightRef.current.simulationId !== simulationId) {
    requestedHighlightRef.current = {
      simulationId,
      value: searchParams.get('highlightAgent'),
    };
  }
  const [loadState, setLoadState] = useState<LoadState>('loading');
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const [executionPhase, setExecutionPhase] = useState<ExecutionPhase>('idle');
  const [setup, setSetup] = useState<SimulationSetup | null>(null);
  const [title, setTitle] = useState('');
  const [agents, setAgents] = useState<SimulationPoint[]>([]);
  const [hazards, setHazards] = useState<EditableHazardZone[]>([]);
  const [selectedExitIds, setSelectedExitIds] = useState<number[]>([]);
  const [highlightedExitId, setHighlightedExitId] = useState<number | null>(null);
  const [highlightedAgentId, setHighlightedAgentId] = useState<number | null>(null);
  const [walkingSpeed, setWalkingSpeed] = useState(1.25);
  const [initialResponseTimeStdDev, setInitialResponseTimeStdDev] = useState(0);
  const [tool, setTool] = useState<SimulationTool>('select');
  const [sprayRadius, setSprayRadius] = useState(1);
  const [eraserRadius, setEraserRadius] = useState(1);
  const [uniformCount, setUniformCount] = useState(100);
  const [selectedHazardId, setSelectedHazardId] = useState<string | null>(null);
  const [message, setMessage] = useState<PageAlert>(null);
  const [agentDeletionToast, setAgentDeletionToast] = useState<AgentDeletionToast>(null);
  const [, setHistoryRevision] = useState(0);
  const placementRef = useRef<PlacementSnapshot>({ agents: [], hazards: [] });
  const pastRef = useRef<PlacementSnapshot[]>([]);
  const futureRef = useRef<PlacementSnapshot[]>([]);
  const gestureOriginRef = useRef<PlacementSnapshot | null>(null);
  const hazardSequenceRef = useRef(0);
  const collapseButtonRef = useRef<HTMLButtonElement>(null);
  const restoreButtonRef = useRef<HTMLButtonElement>(null);
  const panelToggleFocusPendingRef = useRef(false);
  const executing = executionPhase !== 'idle';

  useLayoutEffect(() => {
    if (!panelToggleFocusPendingRef.current) return;
    if (settingsPanel.isMinimized) {
      restoreButtonRef.current?.focus();
      panelToggleFocusPendingRef.current = false;
    } else if (settingsPanel.isExpanding) {
      collapseButtonRef.current?.focus();
      panelToggleFocusPendingRef.current = false;
    }
  }, [settingsPanel.isExpanding, settingsPanel.isMinimized]);

  const replacePlacement = useCallback((next: PlacementSnapshot) => {
    const agentsChanged = placementRef.current.agents !== next.agents;
    placementRef.current = next;
    setAgents(next.agents);
    setHazards(next.hazards);
    if (agentsChanged) setHighlightedAgentId(null);
  }, []);

  const commitPlacement = useCallback(
    (next: PlacementSnapshot) => {
      const current = placementRef.current;
      if (sameSnapshot(current, next)) return;
      pastRef.current = [...pastRef.current.slice(-29), current];
      futureRef.current = [];
      setHistoryRevision((revision) => revision + 1);
      replacePlacement(next);
    },
    [replacePlacement],
  );

  const loadSetup = useCallback(
    (
      data: SimulationSetup,
      highlightAgent: string | null = null,
      resetTool = false,
      selectAllExits = false,
    ) => {
      const loadedHazards = data.hazardZones.map((hazard, index) => ({
        ...hazard,
        clientId: `hazard-${hazard.id ?? index}-${hazardSequenceRef.current++}`,
      }));
      setSetup(data);
      setTitle(data.title || data.drawing.title);
      setSelectedExitIds(
        selectAllExits ? data.drawing.exits.map((exit) => exit.id) : data.selectedExitIds,
      );
      setHighlightedExitId(null);
      setWalkingSpeed(data.walkingSpeed);
      setInitialResponseTimeStdDev(data.initialResponseTimeStdDev);
      if (resetTool) setTool('select');
      setSelectedHazardId(null);
      setAgentDeletionToast(null);
      pastRef.current = [];
      futureRef.current = [];
      gestureOriginRef.current = null;
      setHistoryRevision((revision) => revision + 1);
      replacePlacement({ agents: data.agentPositions, hazards: loadedHazards });
      setHighlightedAgentId(parseHighlightedAgentId(highlightAgent, data.agentPositions.length));
    },
    [replacePlacement],
  );

  useEffect(() => {
    const id = Number(simulationId);
    if (!Number.isInteger(id) || id <= 0) {
      setMessage(errorAlert('잘못된 시뮬레이션 번호입니다.'));
      setLoadState('error');
      return;
    }
    let cancelled = false;
    setLoadState('loading');
    simulationApi
      .getSetup(id)
      .then((data) => {
        if (!cancelled) {
          const defaultAllExits = requestedDefaultAllExitsRef.current.value;
          loadSetup(data, requestedHighlightRef.current.value, true, defaultAllExits);
          if (defaultAllExits) {
            setSearchParams(
              (current) => {
                const next = new URLSearchParams(current);
                next.delete('defaultAllExits');
                return next;
              },
              { replace: true },
            );
          }
          setLoadState('ready');
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setMessage(errorAlert(getSimulationErrorMessage(error)));
          setLoadState('error');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [loadSetup, navigate, setSearchParams, simulationId]);

  useEffect(() => {
    if (loadState !== 'ready' || !searchParams.has('highlightAgent')) return;
    const next = new URLSearchParams(searchParams);
    next.delete('highlightAgent');
    setSearchParams(next, { replace: true });
  }, [loadState, searchParams, setSearchParams]);

  const undo = useCallback(() => {
    const previous = pastRef.current[pastRef.current.length - 1];
    if (!previous) return;
    pastRef.current = pastRef.current.slice(0, -1);
    futureRef.current = [placementRef.current, ...futureRef.current].slice(0, 30);
    setHistoryRevision((revision) => revision + 1);
    replacePlacement(previous);
    setSelectedHazardId(null);
  }, [replacePlacement]);

  const redo = useCallback(() => {
    const next = futureRef.current[0];
    if (!next) return;
    futureRef.current = futureRef.current.slice(1);
    pastRef.current = [...pastRef.current.slice(-29), placementRef.current];
    setHistoryRevision((revision) => revision + 1);
    replacePlacement(next);
    setSelectedHazardId(null);
  }, [replacePlacement]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (agentDeletionToast?.state === 'confirm') return;
      const target = event.target as HTMLElement | null;
      if (target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA') return;
      const modifier = event.ctrlKey || event.metaKey;
      if (modifier && event.key.toLowerCase() === 'z') {
        event.preventDefault();
        if (event.shiftKey) redo();
        else undo();
      } else if (modifier && event.key.toLowerCase() === 'y') {
        event.preventDefault();
        redo();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [agentDeletionToast?.state, redo, undo]);

  useEffect(() => {
    if (agentDeletionToast?.state !== 'success') return;
    const timer = window.setTimeout(() => setAgentDeletionToast(null), 4000);
    return () => window.clearTimeout(timer);
  }, [agentDeletionToast]);

  if (loadState === 'loading') {
    return <CanvasWorkspaceState message="시뮬레이션 설정을 불러오는 중..." />;
  }

  if (loadState === 'error' || setup === null) {
    return (
      <CanvasWorkspaceState
        message={message?.text ?? '시뮬레이션 설정을 불러오지 못했습니다.'}
        actions={
          <Button type="button" className="cursor-pointer" onClick={() => navigate('/drawings')}>
            도면 목록으로 이동
          </Button>
        }
      />
    );
  }

  const editable = setup.status === 'DRAFT';
  const allExitsSelected =
    setup.drawing.exits.length > 0 &&
    setup.drawing.exits.every((exit) => selectedExitIds.includes(exit.id));
  const selectedHazard = hazards.find((hazard) => hazard.clientId === selectedHazardId) ?? null;

  const beginGesture = () => {
    if (editable && gestureOriginRef.current === null) {
      gestureOriginRef.current = placementRef.current;
    }
  };

  const endGesture = () => {
    const origin = gestureOriginRef.current;
    gestureOriginRef.current = null;
    if (origin && !sameSnapshot(origin, placementRef.current)) {
      pastRef.current = [...pastRef.current.slice(-29), origin];
      futureRef.current = [];
      setHistoryRevision((revision) => revision + 1);
    }
  };

  const applySpray = (point: SimulationPoint) => {
    if (!editable) return;
    const current = placementRef.current;
    const nextAgents = addSprayedAgents(point, sprayRadius, setup.drawing, current.agents);
    if (nextAgents !== current.agents) replacePlacement({ ...current, agents: nextAgents });
    if (nextAgents.length >= MAX_AGENTS)
      setMessage(errorAlert(`최대 ${MAX_AGENTS.toLocaleString()}명까지 배치할 수 있습니다.`));
  };

  const applyErase = (point: SimulationPoint) => {
    if (!editable) return;
    const current = placementRef.current;
    const nextAgents = eraseAgents(point, eraserRadius, current.agents);
    if (nextAgents.length !== current.agents.length) {
      replacePlacement({ ...current, agents: nextAgents });
    }
  };

  const createHazard = (point: SimulationPoint) => {
    if (!editable) return;
    const hazard: EditableHazardZone = {
      clientId: `hazard-new-${hazardSequenceRef.current++}`,
      centerX: point.x,
      centerY: point.y,
      radius: 2,
    };
    commitPlacement({ ...placementRef.current, hazards: [...hazards, hazard] });
    setSelectedHazardId(hazard.clientId);
    setTool('select');
  };

  const moveHazard = (clientId: string, point: SimulationPoint) => {
    if (!editable) return;
    const current = placementRef.current;
    replacePlacement({
      ...current,
      hazards: current.hazards.map((hazard) =>
        hazard.clientId === clientId ? { ...hazard, centerX: point.x, centerY: point.y } : hazard,
      ),
    });
  };

  const resizeHazard = (clientId: string, radius: number) => {
    const current = placementRef.current;
    replacePlacement({
      ...current,
      hazards: current.hazards.map((hazard) =>
        hazard.clientId === clientId ? { ...hazard, radius } : hazard,
      ),
    });
  };

  const commitHazardRadius = (clientId: string, radius: number) => {
    if (!editable) return;
    const nextRadius = Math.min(
      HAZARD_MAX_RADIUS,
      Math.max(HAZARD_MIN_RADIUS, Number(radius.toFixed(1))),
    );
    const current = placementRef.current;
    const currentHazard = current.hazards.find((hazard) => hazard.clientId === clientId);
    if (!currentHazard || currentHazard.radius === nextRadius) return;
    commitPlacement({
      ...current,
      hazards: current.hazards.map((hazard) =>
        hazard.clientId === clientId ? { ...hazard, radius: nextRadius } : hazard,
      ),
    });
  };

  const deleteHazard = (clientId: string) => {
    commitPlacement({
      ...placementRef.current,
      hazards: placementRef.current.hazards.filter((hazard) => hazard.clientId !== clientId),
    });
    setSelectedHazardId(null);
  };

  const handleUniformPlacement = () => {
    if (!editable) return;
    setMessage(null);
    if (!Number.isInteger(uniformCount) || uniformCount < 0 || uniformCount > MAX_AGENTS) {
      setMessage(
        errorAlert(`균등 배치 인원은 0명부터 ${MAX_AGENTS.toLocaleString()}명까지 입력해 주세요.`),
      );
      return;
    }
    const result = createUniformPlacement(uniformCount, setup.drawing, setup.randomSeed);
    if (result.capacity < uniformCount) {
      setMessage(
        errorAlert(
          `현재 공간에는 최대 ${result.capacity.toLocaleString()}명까지 균등 배치할 수 있습니다.`,
        ),
      );
      return;
    }
    commitPlacement({ ...placementRef.current, agents: result.positions });
  };

  const handleClearAgents = () => {
    if (!editable || saveState === 'saving' || agents.length === 0) return;
    setAgentDeletionToast({ state: 'confirm', count: agents.length });
  };

  const confirmClearAgents = () => {
    if (!editable || saveState === 'saving' || agents.length === 0) {
      setAgentDeletionToast(null);
      return;
    }
    const count = agents.length;
    commitPlacement({ ...placementRef.current, agents: [] });
    setAgentDeletionToast({ state: 'success', count });
  };

  const validateOptions = (): string | null => {
    if (initialResponseTimeStdDev < 0 || initialResponseTimeStdDev > 600) {
      return '출발시간 표준편차는 0초 이상 600초 이하로 입력해 주세요.';
    }
    if (walkingSpeed <= 0 || walkingSpeed > 3) {
      return '희망 이동속도는 0보다 크고 3.0m/s 이하로 입력해 주세요.';
    }
    return null;
  };

  const saveCurrentSetup = async (): Promise<SimulationSetup | null> => {
    const validationMessage = validateOptions();
    if (validationMessage) {
      setMessage(errorAlert(validationMessage));
      return null;
    }

    setMessage(null);
    try {
      const saved = await simulationApi.updateSetup(setup.simulationId, {
        title: title.trim(),
        walkingSpeed,
        initialResponseTimeStdDev,
        agentPositions: agents,
        hazardZones: hazards.map(({ centerX, centerY, radius }) => ({ centerX, centerY, radius })),
        selectedExitIds,
      });
      loadSetup(saved, highlightedAgentId?.toString() ?? null);
      recordLastActivity('SIMULATION_SETUP', saved.simulationId);
      return saved;
    } catch (error) {
      setMessage(errorAlert(getSimulationErrorMessage(error)));
      return null;
    }
  };

  const handleSave = async () => {
    if (!editable || saveState === 'saving' || executing) return;
    setSaveState('saving');
    const saved = await saveCurrentSetup();
    if (saved) {
      setSaveState('saved');
      window.setTimeout(() => setSaveState('idle'), 1800);
    } else {
      setSaveState('error');
    }
  };

  const handleExecute = async () => {
    if (!editable || executing || saveState === 'saving') return;
    if (agents.length === 0) {
      setMessage(errorAlert('시뮬레이션을 실행하려면 에이전트를 1명 이상 배치해 주세요.'));
      return;
    }
    if (selectedExitIds.length === 0) {
      setMessage(errorAlert('시뮬레이션을 실행하려면 출입구를 1개 이상 선택해 주세요.'));
      return;
    }

    setExecutionPhase('saving');
    setSaveState('saving');
    try {
      const saved = await saveCurrentSetup();
      if (!saved) {
        setSaveState('error');
        return;
      }
      setSaveState('saved');
      setExecutionPhase('validating');
      const validation = await simulationApi.validateRouting(saved.simulationId);
      if (!validation.valid) {
        setMessage(errorAlert(validation.message));
        return;
      }

      const successShownAt = Date.now();
      setMessage({ tone: 'success', text: validation.message });
      setExecutionPhase('requesting');
      await simulationApi.execute(saved.simulationId);
      const remainingSuccessDisplayMs = Math.max(0, 1000 - (Date.now() - successShownAt));
      if (remainingSuccessDisplayMs > 0) {
        await new Promise((resolve) => window.setTimeout(resolve, remainingSuccessDisplayMs));
      }
      navigate('/simulations');
    } catch (error) {
      setSaveState('saved');
      setMessage(errorAlert(getSimulationErrorMessage(error)));
    } finally {
      setExecutionPhase('idle');
    }
  };

  return (
    <CanvasWorkspace className="simulation-setup-workspace">
      <CanvasWorkspaceBackButton
        label="도면 배치"
        aria-label="도면 편집 화면으로 돌아가기"
        onClick={() => navigate(`/layout/${setup.drawing.layoutId}`)}
      />
      <CanvasWorkspaceHeader
        title={title.trim() || setup.title || setup.drawing.title}
        subtitle={`도면: ${setup.drawing.title} · 버전 #${setup.layoutVersionId} · ${setup.modelProfile}`}
        status={editable ? '설정 중' : setup.status}
        statusTone={editable ? 'editing' : 'locked'}
      />

      <section className="simulation-setup-canvas" aria-label="배치 미리보기">
        <SimulationCanvas
          drawing={setup.drawing}
          agents={agents}
          hazards={hazards}
          editable={editable}
          tool={editable ? tool : 'select'}
          brushRadius={tool === 'erase' ? eraserRadius : sprayRadius}
          selectedHazardId={selectedHazardId}
          selectedExitIds={selectedExitIds}
          highlightedExitId={highlightedExitId}
          highlightedAgentId={highlightedAgentId}
          onSpray={applySpray}
          onErase={applyErase}
          onCreateHazard={createHazard}
          onMoveHazard={moveHazard}
          onResizeHazard={resizeHazard}
          onSelectHazard={setSelectedHazardId}
          onGestureStart={beginGesture}
          onGestureEnd={endGesture}
        />
        <div className="simulation-setup-tool-dock" aria-label="배치 도구">
          {TOOL_LABELS.map((item) => (
            <button
              key={item.value}
              type="button"
              disabled={!editable}
              onClick={() => setTool(item.value)}
              aria-pressed={tool === item.value}
              className={`simulation-setup-tool-button ${tool === item.value ? 'is-active' : ''}`}
            >
              {item.label}
            </button>
          ))}
        </div>
        {agentDeletionToast?.state === 'confirm' && (
          <AgentDeletionConfirmDialog
            count={agentDeletionToast.count}
            onCancel={() => setAgentDeletionToast(null)}
            onConfirm={confirmClearAgents}
          />
        )}
        {agentDeletionToast?.state === 'success' && (
          <AgentDeletionSuccessToast
            count={agentDeletionToast.count}
            onClose={() => setAgentDeletionToast(null)}
            className="simulation-setup-agent-toast"
          />
        )}
        {message && (
          <div
            role={message.tone === 'error' ? 'alert' : 'status'}
            aria-live={message.tone === 'success' ? 'polite' : undefined}
            className={`simulation-setup-alert is-${message.tone}`}
          >
            <span>{message.text}</span>
            <button
              type="button"
              onClick={() => setMessage(null)}
              className="shrink-0 rounded p-0.5 outline-none transition hover:opacity-70 focus-visible:ring-2 focus-visible:ring-focus-ring"
              aria-label="알림 닫기"
            >
              <X aria-hidden="true" className="h-4 w-4" />
            </button>
          </div>
        )}
      </section>

      {settingsPanel.isMinimized ? (
        <CanvasWorkspacePanelRestore
          ref={restoreButtonRef}
          aria-controls="simulation-setup-panel"
          aria-expanded="false"
          onClick={() => {
            panelToggleFocusPendingRef.current = true;
            settingsPanel.restore();
          }}
        >
          배치 설정 열기
        </CanvasWorkspacePanelRestore>
      ) : (
        <CanvasWorkspacePanel
          id="simulation-setup-panel"
          ariaLabel="시뮬레이션 배치 설정"
          animate
          className={`simulation-setup-panel ${settingsPanel.isCollapsing ? 'is-collapsing' : ''} ${settingsPanel.isExpanding ? 'is-expanding' : ''}`}
          onAnimationEnd={(event) => {
            if (event.currentTarget === event.target) settingsPanel.handleAnimationEnd();
          }}
        >
          <div className="simulation-setup-panel__top">
            <div className="simulation-setup-panel__heading">
              <div>
                <small>SIMULATION SETUP</small>
                <h2>배치 설정</h2>
              </div>
              <button
                ref={collapseButtonRef}
                type="button"
                aria-controls="simulation-setup-panel"
                aria-expanded="true"
                aria-label="배치 설정 최소화"
                className="simulation-setup-panel__collapse"
                onClick={() => {
                  panelToggleFocusPendingRef.current = true;
                  settingsPanel.collapse();
                }}
              >
                <Minus aria-hidden="true" />
              </button>
            </div>
            <div className="simulation-setup-panel__actions">
              <button
                type="button"
                className="simulation-setup-panel__save"
                onClick={() => void handleSave()}
                disabled={!editable || saveState === 'saving' || executing}
              >
                {saveState === 'saving'
                  ? '저장 중'
                  : saveState === 'saved'
                    ? '저장 완료'
                    : saveState === 'error'
                      ? '저장 실패'
                      : '설정 저장'}
              </button>
              <button
                type="button"
                className="simulation-setup-panel__execute"
                onClick={() => void handleExecute()}
                disabled={!editable || executing || saveState === 'saving'}
                title={
                  agents.length === 0
                    ? '에이전트를 1명 이상 배치해 주세요.'
                    : selectedExitIds.length === 0
                      ? '출입구를 1개 이상 선택해 주세요.'
                      : undefined
                }
              >
                {executionPhase === 'saving'
                  ? '설정 저장 중'
                  : executionPhase === 'validating'
                    ? '경로 검증 중'
                    : executionPhase === 'requesting'
                      ? '실행 요청 중'
                      : '시뮬레이션 실행'}
              </button>
            </div>
          </div>
          <div className="simulation-setup-panel__content">
            <section className="simulation-setup-panel__section">
              <label
                htmlFor="simulation-title-input"
                className="mb-1.5 block text-xs font-bold text-text-muted"
              >
                시뮬레이션 제목
              </label>
              <input
                id="simulation-title-input"
                type="text"
                value={title}
                disabled={!editable}
                maxLength={200}
                onChange={(event) => setTitle(event.target.value)}
                placeholder={setup.drawing.title}
                className="simulation-setup-title-input"
              />
            </section>

            <section className="simulation-setup-panel__summary">
              <div className="flex items-end justify-between">
                <div>
                  <p className="text-xs font-bold text-text-muted">전체 배치 인원</p>
                  <p className="mt-1 text-2xl font-black tabular-nums text-primary">
                    {agents.length.toLocaleString()}명
                  </p>
                </div>
                <p className="text-xs tabular-nums text-text-muted">
                  최대 {MAX_AGENTS.toLocaleString()}명
                </p>
              </div>
            </section>

            <section className="simulation-setup-panel__section">
              <div
                aria-live="polite"
                className={`simulation-setup-tool-state flex items-center justify-between rounded-xl border px-3 py-2 transition-all duration-300 ${
                  tool === 'erase'
                    ? 'scale-[1.02] border-danger/25 bg-danger-soft text-danger'
                    : tool === 'spray'
                      ? 'border-primary/25 bg-primary-soft text-primary'
                      : 'border-line bg-surface text-text-strong'
                }`}
              >
                <div className="flex items-center gap-2">
                  <span
                    className={`simulation-setup-tool-state__icon flex size-7 items-center justify-center rounded-full transition-all duration-300 ${
                      tool === 'erase'
                        ? 'scale-110 bg-white text-danger'
                        : tool === 'spray'
                          ? 'bg-white text-primary'
                          : 'bg-white text-text-muted'
                    }`}
                    aria-hidden="true"
                  >
                    {tool === 'erase' ? (
                      <Minus aria-hidden="true" className="h-4 w-4" />
                    ) : tool === 'spray' ? (
                      <Plus aria-hidden="true" className="h-4 w-4" />
                    ) : (
                      <MousePointer2 aria-hidden="true" className="h-4 w-4" />
                    )}
                  </span>
                  <h2 className="text-sm font-black">
                    {tool === 'erase' ? '에이전트 지우기' : '에이전트 배치'}
                  </h2>
                </div>
                <span className="simulation-setup-tool-state__badge rounded-full bg-white px-2.5 py-1 text-[11px] font-black">
                  {tool === 'erase' ? '지우개 모드' : tool === 'spray' ? '배치 모드' : '도구 대기'}
                </span>
              </div>
              {(tool === 'spray' || tool === 'erase') && (
                <label className="mt-4 block text-xs font-bold text-text-muted">
                  {tool === 'erase' ? '지우개' : '스프레이'} 크기 ·{' '}
                  {(tool === 'erase' ? eraserRadius : sprayRadius).toFixed(1)}m
                  <input
                    type="range"
                    min={AGENT_RADIUS}
                    max={5}
                    step={0.1}
                    value={tool === 'erase' ? eraserRadius : sprayRadius}
                    onChange={(event) => {
                      const radius = Number(event.target.value);
                      if (tool === 'erase') setEraserRadius(radius);
                      else setSprayRadius(radius);
                    }}
                    disabled={!editable}
                    className="mt-2 w-full accent-primary"
                  />
                </label>
              )}
              <div className="mt-4 flex gap-2">
                <div className="min-w-0 flex-1 text-xs font-bold text-text-muted">
                  <label htmlFor="uniform-agent-count">균등 배치 인원</label>
                  <NumberStepperInput
                    id="uniform-agent-count"
                    label="균등 배치 인원"
                    min={0}
                    max={MAX_AGENTS}
                    step={1}
                    value={uniformCount}
                    onValueChange={setUniformCount}
                    disabled={!editable}
                  />
                </div>
                <button
                  type="button"
                  onClick={handleUniformPlacement}
                  disabled={!editable}
                  className="mt-6 h-10 rounded-lg bg-primary-soft px-3 text-xs font-bold text-primary outline-none transition hover:bg-primary/15 focus-visible:ring-2 focus-visible:ring-focus-ring disabled:opacity-40"
                >
                  균등분포 배치
                </button>
              </div>
              <button
                type="button"
                onClick={handleClearAgents}
                disabled={!editable || saveState === 'saving' || agents.length === 0}
                className="mt-3 h-10 w-full rounded-lg border border-danger/25 bg-white text-xs font-bold text-danger-strong outline-none transition hover:bg-danger-soft focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-40"
              >
                에이전트 전체 삭제
              </button>
            </section>

            <section className="simulation-setup-panel__section">
              <h2 className="text-sm font-black">시뮬레이션 조건</h2>
              <div className="simulation-setup-condition-grid mt-4 grid grid-cols-2 gap-x-3 gap-y-4">
                <div className="simulation-setup-condition-field text-xs font-bold text-text-muted">
                  <div className="simulation-setup-condition-label flex gap-1">
                    <label htmlFor="walking-speed">희망 이동속도 (m/s)</label>
                    <InfoTooltip id="walking-speed-help" label="희망 이동속도 안내">
                      에이전트가 방해받지 않을 때 목표로 하는 속도입니다. 일반 자유 보행의 대표
                      평균은 약 1.34m/s이며, 3m/s는 빠른 대피 상황을 고려한 시스템 상한입니다. 실제
                      속도는 혼잡도와 상호작용에 따라 달라집니다.
                      <span className="simulation-setup-info-tooltip__source mt-1 block">
                        출처: Weidmann (1993), ETH Zürich
                      </span>
                    </InfoTooltip>
                  </div>
                  <NumberStepperInput
                    id="walking-speed"
                    label="희망 이동속도"
                    min={0.1}
                    max={3}
                    step={0.05}
                    value={walkingSpeed}
                    onValueChange={setWalkingSpeed}
                    disabled={!editable}
                  />
                </div>
                <div className="simulation-setup-condition-field text-xs font-bold text-text-muted">
                  <div className="simulation-setup-condition-label flex gap-1">
                    <label htmlFor="initial-response-time-std-dev">출발시간 표준편차 (초)</label>
                    <InfoTooltip
                      id="initial-response-time-std-dev-help"
                      label="출발시간 표준편차 안내"
                      align="right"
                    >
                      에이전트별 출발시간의 차이를 나타냅니다. 난수 시드에 따라 정규분포에서
                      결정적으로 생성한 뒤 가장 빠른 에이전트가 시뮬레이션 시작과 동시에 출발하도록
                      모든 시간을 조정합니다. 출발시간은 0초 이상이며 표준편차가 0이면 모든
                      에이전트가 즉시 출발합니다.
                    </InfoTooltip>
                  </div>
                  <NumberStepperInput
                    id="initial-response-time-std-dev"
                    label="출발시간 표준편차"
                    min={0}
                    max={600}
                    step={0.1}
                    value={initialResponseTimeStdDev}
                    onValueChange={setInitialResponseTimeStdDev}
                    disabled={!editable}
                  />
                </div>
              </div>
            </section>

            <section className="simulation-setup-panel__section">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-black">사용 출입구</h2>
                <button
                  type="button"
                  disabled={!editable || setup.drawing.exits.length === 0}
                  onClick={() =>
                    setSelectedExitIds(
                      allExitsSelected ? [] : setup.drawing.exits.map((exit) => exit.id),
                    )
                  }
                  className="simulation-setup-panel__bulk-action rounded-lg border border-primary/40 bg-white px-2.5 py-1 text-xs font-bold text-primary outline-none transition hover:bg-primary-soft focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-not-allowed disabled:opacity-40"
                >
                  {allExitsSelected ? '전체 해제' : '전체 선택'}
                </button>
              </div>
              <p className="mt-1 text-xs leading-5 text-text-muted">
                DRAFT 저장은 출입구를 선택하지 않아도 가능합니다.
              </p>
              <div className="simulation-setup-exit-list">
                {setup.drawing.exits.length === 0 ? (
                  <p className="rounded-lg bg-surface px-3 py-3 text-xs text-text-muted">
                    등록된 출입구가 없습니다.
                  </p>
                ) : (
                  setup.drawing.exits.map((exit) => (
                    <label
                      key={exit.id}
                      onMouseEnter={() => setHighlightedExitId(exit.id)}
                      onMouseLeave={(event) => {
                        if (!event.currentTarget.contains(document.activeElement)) {
                          setHighlightedExitId(null);
                        }
                      }}
                      onFocus={() => setHighlightedExitId(exit.id)}
                      onBlur={(event) => {
                        if (!event.currentTarget.contains(event.relatedTarget)) {
                          setHighlightedExitId(null);
                        }
                      }}
                      className={`simulation-setup-exit-option${
                        selectedExitIds.includes(exit.id) ? ' is-selected' : ''
                      }${highlightedExitId === exit.id ? ' is-highlighted' : ''}`}
                    >
                      <input
                        type="checkbox"
                        checked={selectedExitIds.includes(exit.id)}
                        disabled={!editable}
                        onChange={(event) =>
                          setSelectedExitIds((ids) =>
                            event.target.checked
                              ? [...ids, exit.id]
                              : ids.filter((id) => id !== exit.id),
                          )
                        }
                        className="accent-primary"
                      />
                      <span>{exit.name}</span>
                    </label>
                  ))
                )}
              </div>
            </section>

            <section className="simulation-setup-panel__section">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1">
                  <h2 className="text-sm font-black">위험구역</h2>
                  <InfoTooltip id="hazard-cost-help" label="위험구역 경로 비용 안내">
                    에이전트의 대피 경로를 비교할 때 사용하는 상대 비용입니다.
                    <span className="my-1 block font-mono text-[10px] leading-4 text-white">
                      depth = clamp(1 - 중심거리 / 반지름, 0, 1)
                      <br />원 밖: M = 1
                      <br />원 안: M = 5 × 100^depth
                      <br />
                      간선 비용 = 길이 / 6 × (시작점 M + 4 × 중간점 M + 끝점 M)
                    </span>
                    경계는 5, 반지름 중간은 50, 중심은 500입니다. 전체 경로는 모든 간선 비용을
                    합산하고, 위험구역이 겹치면 가장 큰 M만 적용합니다.
                    <span className="mt-1 block text-white/70">
                      HAZARD_RADIAL_EXP_V3 · 활로가 정의한 상대 비용이며 공인 위험도나 사망확률이
                      아닙니다.
                    </span>
                  </InfoTooltip>
                </div>
                <span className="text-xs tabular-nums text-text-muted">{hazards.length}개</span>
              </div>
              {selectedHazard ? (
                <div className="mt-3 rounded-lg border border-danger/25 bg-danger-soft p-3">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-xs font-bold text-danger-strong">선택 위험구역</span>
                    <button
                      type="button"
                      disabled={!editable}
                      onClick={() => deleteHazard(selectedHazard.clientId)}
                      className="rounded-md border border-danger/35 bg-white px-2.5 py-1 text-[11px] font-bold text-danger-strong outline-none transition hover:bg-danger-soft focus-visible:ring-2 focus-visible:ring-focus-ring disabled:opacity-40"
                    >
                      삭제
                    </button>
                  </div>
                  <div className="mt-3 text-xs font-bold text-danger-strong">
                    <label htmlFor="selected-hazard-radius">반지름 (m)</label>
                    <NumberStepperInput
                      id="selected-hazard-radius"
                      label="위험구역 반지름"
                      min={HAZARD_MIN_RADIUS}
                      max={HAZARD_MAX_RADIUS}
                      step={0.1}
                      value={selectedHazard.radius}
                      onValueChange={(radius) =>
                        commitHazardRadius(selectedHazard.clientId, radius)
                      }
                      disabled={!editable}
                    />
                  </div>
                </div>
              ) : (
                <p className="mt-3 rounded-lg bg-surface px-3 py-3 text-xs leading-5 text-text-muted">
                  위험구역을 선택하면 오른쪽 조절점을 드래그해 크기를 변경할 수 있습니다.
                </p>
              )}
            </section>
          </div>
        </CanvasWorkspacePanel>
      )}
    </CanvasWorkspace>
  );
}

export default SimulationSetupPage;
