import axios from 'axios';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Button } from '../../../components/ui';
import { isCancelledRequest, loadWithRetry } from '../../../api/loadWithRetry';
import { useDelayedLoadingMessage } from '../../../hooks/useDelayedLoadingMessage';
import { SIMULATION_RESULT_LOADING_MESSAGE } from '../../../components/workspace/workspaceLoadingMessages';
import {
  CanvasWorkspace,
  CanvasWorkspaceBackButton,
  CanvasWorkspaceHeader,
  CanvasWorkspaceState,
  useCollapsibleWorkspacePanel,
} from '../../../components/workspace';
import { reportApi } from '../../reports/api/reportApi';
import { layoutSearchApi } from '../../layoutSearch/api/layoutSearchApi';
import { LayoutSearchStartDialog } from '../../layoutSearch/components/LayoutSearchStartDialog';
import { riskApi } from '../../risks/api/riskApi';
import type { Risk } from '../../risks/types/risks';
import { simulationApi } from '../../simulations/api/simulationApi';
import type { SimulationResultSummary } from '../../simulations/types';
import { getSimulationListNavigationState } from '../../simulations/utils/simulationListAction';
import { getSimulationErrorMessage } from '../../simulations/utils/getSimulationErrorMessage';
import { simulationResultProvider } from '../api/simulationResultProvider';
import { EvacuationProgressChart } from '../components/EvacuationProgressChart';
import { ImprovementComparisonPanel } from '../components/ImprovementComparisonPanel';
import { PlaybackControls } from '../components/PlaybackControls';
import { ReportDraftDialog } from '../components/ReportDraftDialog';
import { ResultSummaryPanel } from '../components/ResultSummaryPanel';
import { SimulationPlaybackStage } from '../components/SimulationPlaybackStage';
import { SimulationViewToggle } from '../components/SimulationViewToggle';
import { useRecordLastActivity } from '../../home/hooks/useRecordLastActivity';
import { useSimulationPlayback } from '../hooks/useSimulationPlayback';
import { useSimulationResultChunks } from '../hooks/useSimulationResultChunks';
import type {
  RiskZone,
  SimulationResultSummaryViewModel,
  SimulationResultViewModel,
} from '../types';
import {
  BOTTLENECK_DISPLAY_BATCH_SIZE,
  getNextDisplayedBottleneckCount,
  rankBottlenecks,
} from '../utils/bottleneckDisplay';
import { calculateEvacuationRate } from '../utils/evacuationRate';
import { findImprovedFabricDiff } from '../utils/improvedFabrics';
import { selectFramePair } from '../utils/playback';
import type { SimulationViewMode } from '../rendering/simulationViewMode';
import { createSimulationCameraMemory } from '../rendering/simulationCameraMemory';
import '../simulationResult.css';
import '../simulationResultMotion.css';

const COMPACT_SUPPORT_PANEL_QUERY = '(max-width: 1400px), (max-height: 900px)';
const NARROW_RESULT_VIEWPORT_QUERY = '(max-width: 1100px)';

function matchesMediaQuery(query: string) {
  return typeof window !== 'undefined' && window.matchMedia(query).matches;
}

function toRiskZone(risk: Risk): RiskZone {
  const startX = risk.startX ?? 0;
  const startY = risk.startY ?? 0;
  return {
    id: String(risk.id),
    name: risk.title,
    x: startX,
    y: startY,
    width: (risk.endX ?? startX) - startX,
    height: (risk.endY ?? startY) - startY,
  };
}

function getReportDraftErrorMessage(error: unknown) {
  if (axios.isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? 'AI 보고서 초안을 생성하지 못했습니다.';
  }
  return 'AI 보고서 초안을 생성하지 못했습니다.';
}

interface OriginSimulationResult {
  summary: SimulationResultSummaryViewModel;
  executionResult: SimulationResultSummary;
}

type OriginResultState =
  | { status: 'loading' }
  | { status: 'unavailable' }
  | { status: 'ready'; result: OriginSimulationResult };

interface ResultViewProps {
  summary: SimulationResultSummaryViewModel;
  executionResult: SimulationResultSummary;
  originState: OriginResultState;
}

function ResultView({
  summary: currentSummary,
  executionResult: currentExecution,
  originState,
}: ResultViewProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const [viewingOrigin, setViewingOrigin] = useState(false);
  const originReady = originState.status === 'ready';
  // 재생바와 시간은 그대로 두고 화면에 보이는 시뮬레이션 결과만 바꾼다.
  const summary = viewingOrigin && originReady ? originState.result.summary : currentSummary;
  const executionResult =
    viewingOrigin && originReady ? originState.result.executionResult : currentExecution;
  const playback = useSimulationPlayback(summary.durationSeconds);
  const rankedBottlenecks = useMemo(
    () => rankBottlenecks(summary.bottlenecks),
    [summary.bottlenecks],
  );
  const [displayedBottleneckCount, setDisplayedBottleneckCount] = useState(
    BOTTLENECK_DISPLAY_BATCH_SIZE,
  );
  const displayedBottlenecks = useMemo(
    () => rankedBottlenecks.slice(0, displayedBottleneckCount),
    [displayedBottleneckCount, rankedBottlenecks],
  );
  const chunks = useSimulationResultChunks({
    simulationId: Number(summary.simulationId),
    totalPeople: summary.totalPeople,
    maxDensity: summary.maxDensity,
    currentTimeSeconds: playback.chunkLookupTimeSeconds,
    chunkDurationSeconds: executionResult.timelineChunkDurationSeconds,
    timelineChunkCount: executionResult.timelineChunkCount,
    heatmapChunkCount: executionResult.heatmapChunkCount,
  });
  const result = useMemo<SimulationResultViewModel | null>(
    () =>
      chunks.heatmap && chunks.agentFrames.length > 0
        ? {
            ...summary,
            agentFrames: chunks.agentFrames,
            heatmap: chunks.heatmap,
            evacuationProgress: chunks.evacuationProgress,
          }
        : null,
    [chunks.agentFrames, chunks.evacuationProgress, chunks.heatmap, summary],
  );
  // 기존 배치와 개선 배치를 비교해 바뀐 구조물을 양쪽 화면 모두에서 강조한다.
  // 비교 대상은 항상 (기존 요약, 개선 요약) 고정이고, 활성 화면에 맞는 쪽 인덱스를 쓴다.
  const improvedFabricDiff = useMemo(
    () =>
      originState.status === 'ready'
        ? findImprovedFabricDiff(
            originState.result.summary.drawing.fabrics,
            currentSummary.drawing.fabrics,
          )
        : { originIndexes: [], improvedIndexes: [] },
    [originState, currentSummary.drawing],
  );
  const changedFabricCount = (
    viewingOrigin ? improvedFabricDiff.originIndexes : improvedFabricDiff.improvedIndexes
  ).length;
  const evacuationChart = useCollapsibleWorkspacePanel(() =>
    matchesMediaQuery(NARROW_RESULT_VIEWPORT_QUERY),
  );
  const improvementPanel = useCollapsibleWorkspacePanel(() =>
    matchesMediaQuery(COMPACT_SUPPORT_PANEL_QUERY),
  );
  const [selectedBottleneckId, setSelectedBottleneckId] = useState<number | null>(
    rankedBottlenecks[0]?.id ?? null,
  );
  const [viewMode, setViewMode] = useState<SimulationViewMode>('plan');
  const cameraMemoryRef = useRef(createSimulationCameraMemory());
  const [riskZones, setRiskZones] = useState<RiskZone[]>([]);
  const [riskLoadError, setRiskLoadError] = useState<string | null>(null);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportGenerating, setReportGenerating] = useState(false);
  const [reportError, setReportError] = useState<string | null>(null);
  const [layoutSearchOpen, setLayoutSearchOpen] = useState(false);
  const [layoutSearchStarting, setLayoutSearchStarting] = useState(false);
  const [layoutSearchError, setLayoutSearchError] = useState<string | null>(null);
  const [resultsRevealed, setResultsRevealed] = useState(false);

  const bottlenecksVisible = playback.hasCompletedPlayback || resultsRevealed;
  const currentFrame = result
    ? selectFramePair(result.agentFrames, playback.displayTimeSeconds).previous
    : null;
  const evacuationRate = currentFrame
    ? calculateEvacuationRate(currentFrame.evacuatedCount, summary.totalPeople)
    : 0;
  const firstLoadedFrame = result?.agentFrames[0];
  const lastLoadedFrame = result?.agentFrames[result.agentFrames.length - 1];
  const scrubPreviewOutsideLoadedWindow = Boolean(
    playback.isScrubbing &&
    firstLoadedFrame &&
    lastLoadedFrame &&
    (playback.displayTimeSeconds < firstLoadedFrame.timeSeconds ||
      playback.displayTimeSeconds > lastLoadedFrame.timeSeconds),
  );
  const isPlaybackDataStale = scrubPreviewOutsideLoadedWindow || !chunks.readyForCurrentTime;

  useEffect(() => {
    setDisplayedBottleneckCount(BOTTLENECK_DISPLAY_BATCH_SIZE);
    setSelectedBottleneckId(rankedBottlenecks[0]?.id ?? null);
  }, [rankedBottlenecks, summary.simulationId]);

  useEffect(() => {
    const compactViewport = window.matchMedia(COMPACT_SUPPORT_PANEL_QUERY);
    const minimizeSupportPanel = (event: MediaQueryListEvent) => {
      if (event.matches) improvementPanel.minimize();
    };

    compactViewport.addEventListener('change', minimizeSupportPanel);
    return () => compactViewport.removeEventListener('change', minimizeSupportPanel);
  }, [improvementPanel.minimize]);

  useEffect(() => {
    const narrowViewport = window.matchMedia(NARROW_RESULT_VIEWPORT_QUERY);
    const minimizeEvacuationChart = (event: MediaQueryListEvent) => {
      if (event.matches) evacuationChart.minimize();
    };

    narrowViewport.addEventListener('change', minimizeEvacuationChart);
    return () => narrowViewport.removeEventListener('change', minimizeEvacuationChart);
  }, [evacuationChart.minimize]);

  const handleViewportPan = () => {
    if (!evacuationChart.isMinimized) evacuationChart.collapse();
    if (!improvementPanel.isMinimized) improvementPanel.collapse();
  };

  const handleViewModeChange = (mode: SimulationViewMode) => {
    setViewMode(mode);
  };
  useEffect(() => {
    let active = true;
    setRiskLoadError(null);
    riskApi
      .listByLayout(summary.layoutId)
      .then((risks) => {
        if (active) setRiskZones(risks.filter((risk) => risk.startX !== null).map(toRiskZone));
      })
      .catch(() => {
        if (active) setRiskLoadError('저장된 주의 항목을 불러오지 못했습니다.');
      });
    return () => {
      active = false;
    };
  }, [summary.layoutId]);

  useEffect(() => {
    const navigationState = location.state as { openLayoutSearchStart?: unknown } | null;
    if (navigationState?.openLayoutSearchStart !== true) return;

    setLayoutSearchError(null);
    setLayoutSearchOpen(true);
    navigate(location.pathname, { replace: true, state: null });
  }, [location.pathname, location.state, navigate]);

  const handleRevealResults = () => {
    playback.pause();
    playback.seek(summary.durationSeconds);
    setResultsRevealed(true);
  };

  const handleViewOrigin = (nextViewingOrigin: boolean) => {
    // 전환 대상 결과가 더 짧으면 재생 위치를 그 결과 길이 안으로 되돌린다.
    const target =
      nextViewingOrigin && originState.status === 'ready'
        ? originState.result
        : nextViewingOrigin
          ? null
          : { summary: currentSummary, executionResult: currentExecution };
    if (!target) return;
    setViewingOrigin(nextViewingOrigin);
    if (playback.displayTimeSeconds > target.summary.durationSeconds) {
      playback.seek(target.summary.durationSeconds);
    }
  };

  const handleShowMoreBottlenecks = () => {
    setDisplayedBottleneckCount((currentCount) =>
      getNextDisplayedBottleneckCount(currentCount, rankedBottlenecks.length),
    );
  };

  const handleOpenReport = () => {
    setReportError(null);
    setReportOpen(true);
  };

  const handleGenerateReport = async (comparisonResultIds: number[]) => {
    if (!result) return;
    setReportGenerating(true);
    setReportError(null);
    try {
      await reportApi.createAiDraft({
        sourceSimulationResultId: result.simulationResultId,
        comparisonSimulationResultIds: comparisonResultIds,
      });
      setReportOpen(false);
      navigate('/reports');
    } catch (error) {
      setReportError(getReportDraftErrorMessage(error));
    } finally {
      setReportGenerating(false);
    }
  };

  const handleStartLayoutSearch = async (verify: boolean) => {
    setLayoutSearchStarting(true);
    setLayoutSearchError(null);
    try {
      await layoutSearchApi.start(Number(summary.simulationId), verify);
      setLayoutSearchOpen(false);
      navigate('/simulations', {
        state: { layoutSearchSimulationId: Number(summary.simulationId) },
      });
    } catch (error) {
      setLayoutSearchError(getSimulationErrorMessage(error));
    } finally {
      setLayoutSearchStarting(false);
    }
  };

  if (chunks.loading && (!result || !currentFrame)) {
    return <CanvasWorkspaceState message={SIMULATION_RESULT_LOADING_MESSAGE} role="status" />;
  }

  if (chunks.error || !result || !currentFrame) {
    return (
      <CanvasWorkspaceState
        message={chunks.error ?? '시뮬레이션 재생 데이터가 없습니다.'}
        actions={
          <>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="cursor-pointer"
              onClick={() => navigate('/simulations')}
            >
              뒤로
            </Button>
            {chunks.error && (
              <Button type="button" size="sm" onClick={chunks.retry}>
                다시 시도
              </Button>
            )}
          </>
        }
      />
    );
  }

  return (
    <CanvasWorkspace className="simulation-result-page">
      <SimulationPlaybackStage
        cameraMemory={cameraMemoryRef.current}
        result={result}
        bottlenecks={displayedBottlenecks}
        currentTimeSeconds={playback.displayTimeSeconds}
        selectedBottleneckId={selectedBottleneckId}
        showBottlenecks={bottlenecksVisible}
        riskZones={riskZones}
        viewMode={viewMode}
        improvedFabricIndexes={
          viewingOrigin ? improvedFabricDiff.originIndexes : improvedFabricDiff.improvedIndexes
        }
        onViewportPan={handleViewportPan}
      />

      <div className="result-top-left">
        <CanvasWorkspaceBackButton onClick={() => navigate('/simulations')} />
        {originReady && (
          <div className="origin-toggle" role="group" aria-label="기존 배치와 개선 배치 결과 전환">
            <button
              type="button"
              className={viewingOrigin ? 'is-active' : undefined}
              aria-pressed={viewingOrigin}
              onClick={() => handleViewOrigin(true)}
            >
              기존 배치
            </button>
            <button
              type="button"
              className={!viewingOrigin ? 'is-active' : undefined}
              aria-pressed={!viewingOrigin}
              onClick={() => handleViewOrigin(false)}
            >
              개선 배치
            </button>
          </div>
        )}
        {originReady && changedFabricCount > 0 && (
          <span className="origin-fabric-badge">개선된 구조물 {changedFabricCount}개</span>
        )}
      </div>
      <CanvasWorkspaceHeader
        title={
          <div className="flex items-center gap-2">
            <span>{result.title}</span>
            {result.isImprovement && (
              <span className="inline-flex shrink-0 items-center rounded bg-primary-soft px-2 py-0.5 text-xs font-bold text-primary">
                배치 개선안
              </span>
            )}
          </div>
        }
        subtitle={result.subtitle}
        status="완료"
        statusTone="complete"
      />

      <SimulationViewToggle mode={viewMode} onChange={handleViewModeChange} />

      {riskLoadError && (
        <div className="risk-zone-control">
          <p className="risk-zone-load-error">{riskLoadError}</p>
        </div>
      )}

      <ResultSummaryPanel
        result={result}
        bottlenecks={displayedBottlenecks}
        evacuatedCount={currentFrame.evacuatedCount}
        evacuationRate={evacuationRate}
        isPlaybackDataStale={isPlaybackDataStale}
        bottlenecksVisible={bottlenecksVisible}
        selectedBottleneckId={selectedBottleneckId}
        displayedBottleneckCount={displayedBottlenecks.length}
        totalBottleneckCount={rankedBottlenecks.length}
        reserveImprovementPanelSpace={!improvementPanel.isMinimized}
        riskZones={riskZones}
        onSelectBottleneck={setSelectedBottleneckId}
        onShowMoreBottlenecks={handleShowMoreBottlenecks}
        onOpenReport={handleOpenReport}
        onOpenRisk={(riskId) => navigate(`/risk-management?riskId=${riskId}`)}
      />

      {evacuationChart.isMinimized ? (
        <button
          type="button"
          className="evacuation-chart-restore"
          onClick={evacuationChart.restore}
        >
          시간별 대피 인원 열기
        </button>
      ) : (
        <EvacuationProgressChart
          points={result.evacuationProgress}
          currentTime={playback.displayTimeSeconds}
          duration={result.durationSeconds}
          totalPeople={result.totalPeople}
          isPlaybackDataStale={isPlaybackDataStale}
          isCollapsing={evacuationChart.isCollapsing}
          isExpanding={evacuationChart.isExpanding}
          onCollapseEnd={evacuationChart.handleAnimationEnd}
          onExpandEnd={evacuationChart.handleAnimationEnd}
        />
      )}

      <PlaybackControls
        currentTimeSeconds={playback.displayTimeSeconds}
        durationSeconds={result.durationSeconds}
        isPlaying={playback.isPlaying}
        playbackRate={playback.playbackRate}
        resultsVisible={bottlenecksVisible}
        isBuffering={isPlaybackDataStale}
        onToggle={playback.toggle}
        onSeek={playback.seek}
        onScrubStart={playback.startScrub}
        onScrubChange={playback.scrubTo}
        onScrubEnd={playback.endScrub}
        onPlaybackRateChange={playback.setPlaybackRate}
        onRevealResults={handleRevealResults}
      />

      <ImprovementComparisonPanel
        panel={improvementPanel}
        onCompare={() => {
          setLayoutSearchError(null);
          setLayoutSearchOpen(true);
        }}
      />

      <LayoutSearchStartDialog
        open={layoutSearchOpen}
        drawingTitle={result.drawing.name}
        starting={layoutSearchStarting}
        errorMessage={layoutSearchError}
        onClose={() => {
          if (!layoutSearchStarting) setLayoutSearchOpen(false);
        }}
        onEditConstraints={() =>
          navigate(`/layout/${result.layoutId}`, {
            state: {
              returnTo: `/simulations/${result.simulationId}/results`,
              returnLabel: '시뮬레이션 결과',
            },
          })
        }
        onStart={(verify) => void handleStartLayoutSearch(verify)}
      />

      <ReportDraftDialog
        open={reportOpen}
        result={result}
        isGenerating={reportGenerating}
        errorMessage={reportError}
        onClose={() => {
          if (!reportGenerating) setReportOpen(false);
        }}
        onGenerate={handleGenerateReport}
      />
    </CanvasWorkspace>
  );
}

export default function SimulationResultPage() {
  const { simulationId = '' } = useParams();
  const navigate = useNavigate();
  const numericSimulationId = Number(simulationId);
  const recordLastActivity = useRecordLastActivity();
  const [summary, setSummary] = useState<SimulationResultSummaryViewModel | null>(null);
  const [executionResult, setExecutionResult] = useState<SimulationResultSummary | null>(null);
  const [originState, setOriginState] = useState<OriginResultState>({ status: 'loading' });
  const [status, setStatus] = useState<'loading' | 'ready' | 'missing' | 'error'>('loading');
  const [retry, setRetry] = useState(0);
  const loadingMessage = useDelayedLoadingMessage(
    status === 'loading',
    SIMULATION_RESULT_LOADING_MESSAGE,
  );

  useEffect(() => {
    const controller = new AbortController();
    setStatus('loading');
    if (!Number.isSafeInteger(numericSimulationId) || numericSimulationId < 1) {
      setStatus('missing');
      return () => {
        controller.abort();
      };
    }
    loadWithRetry(() => simulationApi.getExecution(numericSimulationId, controller.signal), {
      signal: controller.signal,
    })
      .then(async (execution) => {
        if (controller.signal.aborted) return;
        if (execution.status !== 'COMPLETED' || !execution.result) {
          navigate('/simulations', {
            replace: true,
            state: getSimulationListNavigationState({
              id: numericSimulationId,
              status: execution.status,
            }),
          });
          return;
        }
        const loadedSummary = await loadWithRetry(
          () => simulationResultProvider.getSummary(simulationId, controller.signal),
          { signal: controller.signal },
        );
        if (controller.signal.aborted) return;
        if (!loadedSummary) {
          setStatus('missing');
          return;
        }
        setExecutionResult(execution.result);
        setSummary(loadedSummary);
        setStatus('ready');
        // 결과 분석 화면은 별도 저장이 없으므로 결과를 실제로 열람한 시점을 작업으로 본다.
        recordLastActivity('SIMULATION_RESULT', numericSimulationId);
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted && !isCancelledRequest(error)) setStatus('error');
      });
    return () => {
      controller.abort();
    };
  }, [navigate, numericSimulationId, retry, simulationId, recordLastActivity]);

  useEffect(() => {
    // 개선안이 아니면 기존 시뮬레이션 비교 자체가 없으므로 loading에 머물지 않게 정리한다.
    if (!summary?.isImprovement || !summary.sourceSimulationId) {
      setOriginState({ status: 'unavailable' });
      return;
    }
    const controller = new AbortController();
    setOriginState({ status: 'loading' });
    const sourceSimulationId = summary.sourceSimulationId;
    Promise.all([
      loadWithRetry(() => simulationApi.getExecution(sourceSimulationId, controller.signal), {
        signal: controller.signal,
      }),
      loadWithRetry(
        () => simulationResultProvider.getSummary(String(sourceSimulationId), controller.signal),
        { signal: controller.signal },
      ),
    ])
      .then(([execution, sourceSummary]) => {
        if (controller.signal.aborted) return;
        // 기존 시뮬레이션이 아직 완료되지 않았거나 결과가 없으면 토글을 숨긴다.
        if (execution.status === 'COMPLETED' && execution.result && sourceSummary) {
          setOriginState({
            status: 'ready',
            result: { summary: sourceSummary, executionResult: execution.result },
          });
        } else {
          setOriginState({ status: 'unavailable' });
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setOriginState({ status: 'unavailable' });
      });
    return () => {
      controller.abort();
    };
  }, [summary]);

  if (status === 'loading') {
    return <CanvasWorkspaceState message={loadingMessage} />;
  }
  if (status !== 'ready' || !summary || !executionResult) {
    return (
      <CanvasWorkspaceState
        message={status === 'missing' ? '완료된 결과가 없습니다.' : '결과를 불러오지 못했습니다.'}
        actions={
          <>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="cursor-pointer"
              onClick={() => navigate('/simulations')}
            >
              뒤로
            </Button>
            {status === 'error' && (
              <Button type="button" size="sm" onClick={() => setRetry((value) => value + 1)}>
                다시 시도
              </Button>
            )}
          </>
        }
      />
    );
  }
  return (
    <ResultView summary={summary} executionResult={executionResult} originState={originState} />
  );
}
