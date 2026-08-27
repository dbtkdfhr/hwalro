import { CircleCheck } from 'lucide-react';
import {
  CanvasWorkspacePanel,
  CanvasWorkspacePanelRestore,
  useCollapsibleWorkspacePanel,
} from '../../../components/workspace';
import type { DetectedBottleneck, RiskZone, SimulationResultViewModel } from '../types';
import { BOTTLENECK_DISPLAY_BATCH_SIZE } from '../utils/bottleneckDisplay';
import { getEvacuationProgressDisplay } from '../utils/evacuationProgressDisplay';

interface Props {
  result: SimulationResultViewModel;
  bottlenecks: DetectedBottleneck[];
  evacuatedCount: number;
  evacuationRate: number;
  isPlaybackDataStale: boolean;
  bottlenecksVisible: boolean;
  selectedBottleneckId: number | null;
  displayedBottleneckCount: number;
  totalBottleneckCount: number;
  reserveImprovementPanelSpace: boolean;
  riskZones: RiskZone[];
  onSelectBottleneck: (id: number) => void;
  onShowMoreBottlenecks: () => void;
  onOpenReport: () => void;
  onOpenRisk: (id: number) => void;
}

export function ResultSummaryPanel({
  result,
  bottlenecks,
  evacuatedCount,
  evacuationRate,
  isPlaybackDataStale,
  bottlenecksVisible,
  selectedBottleneckId,
  displayedBottleneckCount,
  totalBottleneckCount,
  reserveImprovementPanelSpace,
  riskZones,
  onSelectBottleneck,
  onShowMoreBottlenecks,
  onOpenReport,
  onOpenRisk,
}: Props) {
  const panel = useCollapsibleWorkspacePanel();
  const selectedBottleneck = bottlenecks.find((item) => item.id === selectedBottleneckId);
  const remainingBottleneckCount = totalBottleneckCount - displayedBottleneckCount;
  const nextBottleneckCount = Math.min(BOTTLENECK_DISPLAY_BATCH_SIZE, remainingBottleneckCount);
  const evacuationProgress = getEvacuationProgressDisplay(
    evacuatedCount,
    evacuationRate,
    isPlaybackDataStale,
  );

  if (panel.isMinimized) {
    return (
      <CanvasWorkspacePanelRestore className="summary-restore" onClick={panel.restore}>
        결과 요약 열기
      </CanvasWorkspacePanelRestore>
    );
  }

  return (
    <CanvasWorkspacePanel
      ariaLabel="시뮬레이션 결과 요약"
      className={`result-summary ${panel.isCollapsing ? 'is-collapsing' : ''} ${reserveImprovementPanelSpace ? 'has-improvement-panel' : ''}`}
      onAnimationEnd={(event) => {
        if (event.currentTarget === event.target) panel.handleAnimationEnd();
      }}
    >
      <div className="summary-header">
        <div>
          <small>SIMULATION RESULT</small>
          <h1 style={{ fontWeight: 900, lineHeight: 1.08, letterSpacing: '-0.04em' }}>결과 요약</h1>
        </div>
        <button type="button" aria-label="결과 요약 최소화" onClick={panel.collapse}>
          −
        </button>
      </div>
      <div className="result-summary-scroll-content">
        <div className="metric-grid">
          <div className="metric metric-wide">
            <span>총 대피 시간</span>
            <strong>{result.durationSeconds}초</strong>
          </div>
          <div className="metric">
            <span>최대 밀집도</span>
            <strong>{result.maxDensity}명/㎡</strong>
          </div>
          <div className="metric">
            <span>병목 구간</span>
            <strong>{result.bottlenecks.length}곳</strong>
          </div>
        </div>
        <div className="evacuation-complete" aria-busy={isPlaybackDataStale} aria-live="polite">
          <span>대피 진행 · {evacuationProgress.countLabel}</span>
          <strong>{evacuationProgress.rateLabel}</strong>
        </div>
        <h2>병목 분석</h2>
        {!bottlenecksVisible ? (
          <div className="bottleneck-analysis-locked" role="status">
            <strong>병목 상세 분석 대기</strong>
            <span>
              시뮬레이션을 끝까지 재생하거나
              <br />
              하단의 결과 보기를 눌러 확인하세요.
            </span>
          </div>
        ) : totalBottleneckCount === 0 ? (
          <div className="bottleneck-analysis-empty" role="status">
            <CircleCheck aria-hidden="true" />
            <div>
              <strong>감지된 병목 구간이 없습니다</strong>
              <span>이번 결과에서는 기준 밀집도를 초과한 병목 구간이</span>
              <span> 확인되지 않았습니다.</span>
            </div>
          </div>
        ) : (
          <>
            <div id="bottleneck-list" className="bottleneck-list">
              {bottlenecks.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={selectedBottleneckId === item.id ? 'is-selected' : ''}
                  onClick={() => onSelectBottleneck(item.id)}
                >
                  <strong>{item.name}</strong>
                  <span>
                    {Math.round(item.endTimeSeconds - item.startTimeSeconds)}초 동안 기준 밀집도
                    초과
                  </span>
                  <em>{item.peakDensity}명/㎡</em>
                </button>
              ))}
            </div>
            {remainingBottleneckCount > 0 && (
              <button
                type="button"
                className="bottleneck-list-toggle"
                aria-controls="bottleneck-list"
                onClick={onShowMoreBottlenecks}
              >
                병목 {nextBottleneckCount}개 더 보기 · {displayedBottleneckCount}/
                {totalBottleneckCount}
              </button>
            )}
            {selectedBottleneck && (
              <p className="analysis-note">
                기준 {selectedBottleneck.thresholdValue}명/㎡ · 최고{' '}
                {selectedBottleneck.peakDensity}
                명/㎡
              </p>
            )}
          </>
        )}
        {riskZones.length > 0 && (
          <section className="risk-zone-summary" aria-labelledby="risk-zone-summary-title">
            <h2 id="risk-zone-summary-title">주의 항목</h2>
            <div className="risk-zone-list">
              {riskZones.map((zone) => (
                <button
                  type="button"
                  className="risk-zone-summary-card cursor-pointer"
                  key={zone.id}
                  aria-label={`${zone.name} 주의 항목 관리로 이동`}
                  onClick={() => onOpenRisk(Number(zone.id))}
                >
                  <strong>{zone.name}</strong>
                  <span>사용자 지정 주의 항목</span>
                </button>
              ))}
            </div>
          </section>
        )}
        <button type="button" className="primary-action" onClick={onOpenReport}>
          AI 보고서 초안 생성
        </button>
      </div>
    </CanvasWorkspacePanel>
  );
}
