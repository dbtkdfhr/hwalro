import axios from 'axios';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button } from '../../../components/ui';
import {
  CanvasWorkspace,
  CanvasWorkspaceBackButton,
  CanvasWorkspaceHeader,
  CanvasWorkspaceState,
  useCollapsibleWorkspacePanel,
} from '../../../components/workspace';
import { reportApi } from '../../reports/api/reportApi';
import { riskApi } from '../../risks/api/riskApi';
import type { Risk } from '../../risks/types/risks';
import { simulationApi } from '../../simulations/api/simulationApi';
import type { SimulationResultSummary } from '../../simulations/types';
import { getSimulationListNavigationState } from '../../simulations/utils/simulationListAction';
import { simulationResultProvider } from '../api/simulationResultProvider';
import { EvacuationProgressChart } from '../components/EvacuationProgressChart';
import { ImprovementComparisonPanel } from '../components/ImprovementComparisonPanel';
import { PlaybackControls } from '../components/PlaybackControls';
import { ReportDraftDialog } from '../components/ReportDraftDialog';
import { ResultSummaryPanel } from '../components/ResultSummaryPanel';
import { RiskZoneEditorDialog } from '../components/RiskZoneEditorDialog';
import { SimulationPlaybackStage } from '../components/SimulationPlaybackStage';
import { useRecordLastActivity } from '../../home/hooks/useRecordLastActivity';
import { useSimulationPlayback } from '../hooks/useSimulationPlayback';
import { useSimulationResultChunks } from '../hooks/useSimulationResultChunks';
import type {
  Bounds,
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
import { selectFramePair } from '../utils/playback';
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

interface ResultViewProps {
  summary: SimulationResultSummaryViewModel;
  executionResult: SimulationResultSummary;
}

function ResultView({ summary, executionResult }: ResultViewProps) {
  const navigate = useNavigate();
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
  const evacuationChart = useCollapsibleWorkspacePanel(() =>
    matchesMediaQuery(NARROW_RESULT_VIEWPORT_QUERY),
  );
  const improvementPanel = useCollapsibleWorkspacePanel(() =>
    matchesMediaQuery(COMPACT_SUPPORT_PANEL_QUERY),
  );
  const [selectedBottleneckId, setSelectedBottleneckId] = useState<number | null>(
    rankedBottlenecks[0]?.id ?? null,
  );
  const [riskDrawingMode, setRiskDrawingMode] = useState(false);
  const [riskZones, setRiskZones] = useState<RiskZone[]>([]);
  const [pendingBounds, setPendingBounds] = useState<Bounds | null>(null);
  const [riskLoadError, setRiskLoadError] = useState<string | null>(null);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportGenerating, setReportGenerating] = useState(false);
  const [reportError, setReportError] = useState<string | null>(null);
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

  const handleRiskZoneCreated = (bounds: Bounds) => {
    setPendingBounds(bounds);
    setRiskDrawingMode(false);
  };

  useEffect(() => {
    let active = true;
    setRiskLoadError(null);
    riskApi
      .listBySimulationResult(summary.simulationResultId)
      .then((risks) => {
        if (active) setRiskZones(risks.filter((risk) => risk.startX !== null).map(toRiskZone));
      })
      .catch(() => {
        if (active) setRiskLoadError('저장된 위험 항목을 불러오지 못했습니다.');
      });
    return () => {
      active = false;
    };
  }, [summary.simulationResultId]);

  const handleRevealResults = () => {
    playback.pause();
    playback.seek(summary.durationSeconds);
    setResultsRevealed(true);
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

  if (chunks.error || !result || !currentFrame) {
    return (
      <CanvasWorkspaceState
        message={
          chunks.error ??
          (chunks.loading
            ? '시뮬레이션 재생 데이터를 불러오는 중입니다.'
            : '시뮬레이션 재생 데이터가 없습니다.')
        }
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
        result={result}
        bottlenecks={displayedBottlenecks}
        currentTimeSeconds={playback.displayTimeSeconds}
        selectedBottleneckId={selectedBottleneckId}
        showBottlenecks={bottlenecksVisible}
        riskDrawingMode={riskDrawingMode}
        riskZones={riskZones}
        onRiskZoneCreated={handleRiskZoneCreated}
        onViewportPan={handleViewportPan}
      />

      <CanvasWorkspaceBackButton onClick={() => navigate('/simulations')} />
      <CanvasWorkspaceHeader
        title={result.title}
        subtitle={result.subtitle}
        status="완료"
        statusTone="complete"
      />

      <div className="risk-zone-control">
        <button
          type="button"
          className={`risk-zone-button ${riskDrawingMode ? 'is-active' : ''}`}
          aria-pressed={riskDrawingMode}
          onClick={() => setRiskDrawingMode((value) => !value)}
        >
          {riskDrawingMode ? '도면을 드래그해 구역을 설정하세요' : '위험 예상 항목 설정'}
        </button>
        {riskLoadError && <p className="risk-zone-load-error">{riskLoadError}</p>}
      </div>

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
        onCompare={() => navigate(`/simulations/${result.simulationId}/layout-search`)}
      />

      {pendingBounds && (
        <RiskZoneEditorDialog
          bounds={pendingBounds}
          drawing={result.drawing}
          simulationResultId={summary.simulationResultId}
          onCancel={() => setPendingBounds(null)}
          onConfirm={(risk) => {
            setRiskZones((zones) => [...zones, toRiskZone(risk)]);
            setPendingBounds(null);
          }}
        />
      )}

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
  const [loadingTotalPeople, setLoadingTotalPeople] = useState<number | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'missing' | 'error'>('loading');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    setStatus('loading');
    setLoadingTotalPeople(null);
    if (!Number.isSafeInteger(numericSimulationId) || numericSimulationId < 1) {
      setStatus('missing');
      return () => {
        active = false;
      };
    }
    void simulationApi
      .getSetup(numericSimulationId)
      .then((setup) => {
        if (active) setLoadingTotalPeople(setup.totalPeople);
      })
      .catch(() => undefined);
    simulationApi
      .getExecution(numericSimulationId)
      .then(async (execution) => {
        if (!active) return;
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
        const loadedSummary = await simulationResultProvider.getSummary(simulationId);
        if (!active) return;
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
      .catch(() => active && setStatus('error'));
    return () => {
      active = false;
    };
  }, [navigate, numericSimulationId, retry, simulationId, recordLastActivity]);

  if (status === 'loading') {
    const participantLabel = loadingTotalPeople?.toLocaleString('ko-KR');
    return (
      <CanvasWorkspaceState
        message={
          participantLabel
            ? `${participantLabel}명 시뮬레이션 결과를 준비하고 있습니다.`
            : '시뮬레이션 결과를 준비하고 있습니다.'
        }
      />
    );
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
  return <ResultView summary={summary} executionResult={executionResult} />;
}
