import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  CanvasWorkspace,
  CanvasWorkspaceBackButton,
  CanvasWorkspaceHeader,
  CanvasWorkspacePanel,
  CanvasWorkspaceState,
  useCollapsibleWorkspacePanel,
} from '../../../components/workspace';
import { simulationApi } from '../../simulations/api/simulationApi';
import type { SimulationDrawing, SimulationSetup } from '../../simulations/types';
import { getSimulationErrorMessage } from '../../simulations/utils/getSimulationErrorMessage';

import { CandidateDetailPanel } from '../components/CandidateDetailPanel';
import { CandidateTabs } from '../components/NoImprovementPanel';
import { ConstraintInspector } from '../components/ConstraintInspector';
import { HoldToCompare } from '../components/HoldToCompare';
import { SearchProgressHeader } from '../components/SearchProgressHeader';
import { useLayoutSearch } from '../hooks/useLayoutSearch';
import { applyChangeSet } from '../utils/applyChangeSet';
import '../layoutSearch.css';

export function changedFabricIds(baseline: SimulationDrawing, after: SimulationDrawing) {
  const afterById = new Map(after.fabrics.map((fabric) => [fabric.id, fabric]));
  const changed = new Set<number>();
  baseline.fabrics.forEach((fabric) => {
    const next = afterById.get(fabric.id);
    if (
      next &&
      (fabric.startX !== next.startX ||
        fabric.startY !== next.startY ||
        fabric.endX !== next.endX ||
        fabric.endY !== next.endY ||
        fabric.rotation !== next.rotation)
    ) {
      changed.add(fabric.id);
    }
  });
  return changed;
}

export default function LayoutSearchPage() {
  const { simulationId = '' } = useParams();
  const navigate = useNavigate();
  const id = Number(simulationId);
  const [sourceSetup, setSourceSetup] = useState<SimulationSetup | null>(null);
  const [sourceLoading, setSourceLoading] = useState(true);
  const [sourceError, setSourceError] = useState<string | null>(null);
  const [selectedTabKey, setSelectedTabKey] = useState<string | null>(null);
  const detailPanel = useCollapsibleWorkspacePanel();

  const {
    search,
    hasSearch,
    loading,
    starting,
    cancelling,
    preparingCandidateIds,
    errorMessage,
    active,
    constraints,
    initialize,
    start,
    cancel,
    prepareSimulation,
    updateConstraints,
    resetToSetup,
    rejectCandidate,
  } = useLayoutSearch(id);

  const loadSourceSetup = useCallback(async () => {
    if (!Number.isSafeInteger(id) || id < 1) {
      setSourceError('올바르지 않은 시뮬레이션 번호입니다.');
      setSourceLoading(false);
      return;
    }
    setSourceLoading(true);
    setSourceError(null);
    try {
      setSourceSetup(await simulationApi.getSetup(id));
    } catch (error) {
      setSourceError(getSimulationErrorMessage(error));
    } finally {
      setSourceLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void loadSourceSetup();
  }, [loadSourceSetup]);

  useEffect(() => {
    setSelectedTabKey(null);
  }, [search?.searchId]);

  const improvedCandidates = search?.improvedCandidates ?? [];

  // 거부된 후보는 "그 자리에 넣을 수 없다"는 사실일 뿐 제안이 아니므로 탭에 올리지 않는다.
  const defaultTabKey = useMemo(
    () => (improvedCandidates.length > 0 ? `i-${improvedCandidates[0].candidateId}` : null),
    [improvedCandidates],
  );

  const activeTabKey = selectedTabKey ?? defaultTabKey;

  const selectedCandidate = useMemo(() => {
    if (activeTabKey === null) {
      return null;
    }
    const candidateId = Number(activeTabKey.slice(2));
    return improvedCandidates.find((entry) => entry.candidateId === candidateId) ?? null;
  }, [activeTabKey, improvedCandidates]);

  const preview = useMemo(() => {
    if (!sourceSetup || !selectedCandidate) {
      return { drawing: null, error: null };
    }
    return applyChangeSet(sourceSetup.drawing, selectedCandidate.changeSet);
  }, [sourceSetup, selectedCandidate]);

  const changedIds = useMemo(() => {
    if (!sourceSetup || !preview.drawing) {
      return undefined;
    }
    return changedFabricIds(sourceSetup.drawing, preview.drawing);
  }, [sourceSetup, preview.drawing]);

  const runSearch = useCallback(
    async (verify: boolean) => {
      if (await start(undefined, verify)) {
        setSelectedTabKey(null);
      }
    },
    [start],
  );

  const retry = useCallback(() => {
    void Promise.all([loadSourceSetup(), initialize()]);
  }, [initialize, loadSourceSetup]);

  const focusComparison = useCallback(() => {
    document.getElementById('compare-improved-button')?.focus();
  }, []);

  if (loading || sourceLoading) {
    return <CanvasWorkspaceState message="배치 개선안 탐색을 준비하고 있습니다." role="status" />;
  }

  if (sourceError || (!hasSearch && errorMessage)) {
    return (
      <CanvasWorkspaceState
        message={sourceError ?? errorMessage}
        actions={
          <>
            <button type="button" className="canvas-workspace-state__btn" onClick={retry}>
              다시 시도
            </button>
            <button
              type="button"
              className="canvas-workspace-state__btn"
              onClick={() => navigate(`/simulations/${id}/results`)}
            >
              결과 화면
            </button>
          </>
        }
      />
    );
  }

  if (!hasSearch || !search) {
    return (
      <CanvasWorkspace className="layout-search-workspace">
        <CanvasWorkspaceBackButton
          label="시뮬레이션 결과"
          onClick={() => navigate(`/simulations/${id}/results`)}
        />
        <CanvasWorkspaceHeader
          title={sourceSetup?.drawing.title || '도면'}
          subtitle="구조물 제약 설정"
          status="제약 설정"
          statusTone="editing"
        />
        {sourceSetup && (
          <ConstraintInspector
            drawing={sourceSetup.drawing}
            constraints={constraints}
            onChange={updateConstraints}
            onStart={(verify) => void runSearch(verify)}
            starting={starting}
          />
        )}
      </CanvasWorkspace>
    );
  }

  return (
    <CanvasWorkspace className="layout-search-workspace">
      <CanvasWorkspaceBackButton
        label="시뮬레이션 결과"
        onClick={() => navigate(`/simulations/${id}/results`)}
      />

      {errorMessage && (
        <div className="search-action-error-floating" role="alert">
          <span>{errorMessage}</span>
          <button type="button" onClick={() => void initialize()}>
            새로고침
          </button>
        </div>
      )}

      {/* 상단 플로팅 탭 바 */}
      {improvedCandidates.length > 0 && (
        <div className="layout-search-floating-tabs">
          <CandidateTabs
            improved={search.improvedCandidates}
            activeKey={activeTabKey ?? ''}
            onSelect={setSelectedTabKey}
          />
        </div>
      )}

      {/* 전체 화면 도면 비교 뷰포트 */}
      <div className="layout-search-stage">
        {selectedCandidate && sourceSetup && preview.drawing ? (
          <HoldToCompare
            before={sourceSetup.drawing}
            after={preview.drawing}
            changedFabricIds={changedIds}
          />
        ) : preview.error ? (
          <div className="proposal-layout-error" role="alert">
            <p>{preview.error}</p>
            <button type="button" onClick={() => void loadSourceSetup()}>
              원본 배치 다시 불러오기
            </button>
          </div>
        ) : (
          <div className="proposal-layout-loading" role="status">
            {active
              ? '개선안이 검증되면 배치를 표시합니다.'
              : search.status === 'NO_IMPROVEMENT'
                ? '현재 탐색 범위에서 개선안을 찾지 못했습니다'
                : '검증 중인 배치'}
          </div>
        )}
      </div>

      {/* 우측 플로팅 패널 (후보 상세 정보) */}
      {detailPanel.isMinimized ? (
        <button
          type="button"
          className="canvas-workspace-panel-restore"
          onClick={detailPanel.restore}
        >
          개선안 상세 보기
        </button>
      ) : (
        <CanvasWorkspacePanel
          ariaLabel="개선안 상세"
          animate
          className={`candidate-detail-panel-container ${detailPanel.isCollapsing ? 'is-collapsing' : ''}`}
        >
          {selectedCandidate ? (
            <CandidateDetailPanel
              candidate={selectedCandidate}
              onPrepareSimulation={() => void prepareSimulation(selectedCandidate.candidateId)}
              preparing={preparingCandidateIds.has(selectedCandidate.candidateId)}
              onContinueComparing={focusComparison}
              previewAvailable={preview.drawing !== null}
              onReject={() => void rejectCandidate(selectedCandidate.candidateId)}
              rejecting={starting}
              onMinimize={detailPanel.minimize}
            />
          ) : (
            <div className="search-insight-empty">
              <div className="search-insight__header">
                <h2>
                  {search.status === 'NO_IMPROVEMENT'
                    ? '현재 탐색 범위에서 개선안을 찾지 못했습니다'
                    : '검증 중인 배치'}
                </h2>
                <p>
                  {search.status === 'NO_IMPROVEMENT'
                    ? '구조물 제약을 조정한 뒤 다시 탐색하면 다른 배치안을 찾을 수 있습니다.'
                    : '개선안이 검증되면 상세 비교를 볼 수 있습니다.'}
                </p>
              </div>
            </div>
          )}
        </CanvasWorkspacePanel>
      )}

      {/* 하단 플로팅 진행/제어 바 */}
      <SearchProgressHeader
        search={search}
        onCancel={() => void cancel()}
        cancelling={cancelling}
        onRerun={resetToSetup}
        rerunning={false}
      />
    </CanvasWorkspace>
  );
}
