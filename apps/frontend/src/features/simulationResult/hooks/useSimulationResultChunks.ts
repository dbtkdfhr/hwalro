import { useEffect, useMemo, useRef, useState } from 'react';
import { timelineChunkWindow } from '../../simulations/utils/timeline';
import { simulationResultProvider } from '../api/simulationResultProvider';
import { isCancelledRequest, loadWithRetry } from '../../../api/loadWithRetry';
import type { EvacuationPoint, HeatmapData, SimulationPlaybackChunkData } from '../types';

interface Options {
  simulationId: number;
  totalPeople: number;
  maxDensity: number;
  currentTimeSeconds: number;
  chunkDurationSeconds: number;
  timelineChunkCount: number;
  heatmapChunkCount: number;
}

interface PlaybackWindow {
  sequences: number[];
  agentFrames: SimulationPlaybackChunkData['agentFrames'];
  heatmap: HeatmapData;
}

function mergeWindow(chunks: SimulationPlaybackChunkData[]): PlaybackWindow {
  const ordered = [...chunks].sort((left, right) => left.sequence - right.sequence);
  const first = ordered[0];
  if (!first) throw new Error('불러온 시뮬레이션 재생 청크가 없습니다.');
  for (const chunk of ordered.slice(1)) {
    const grid = chunk.heatmap;
    const initialGrid = first.heatmap;
    if (
      grid.originX !== initialGrid.originX ||
      grid.originY !== initialGrid.originY ||
      grid.columns !== initialGrid.columns ||
      grid.rows !== initialGrid.rows ||
      grid.cellWidth !== initialGrid.cellWidth ||
      grid.cellHeight !== initialGrid.cellHeight
    ) {
      throw new Error('히트맵 청크의 격자 정보가 일치하지 않습니다.');
    }
  }
  return {
    sequences: ordered.map((chunk) => chunk.sequence),
    agentFrames: ordered.flatMap((chunk) => chunk.agentFrames),
    heatmap: {
      ...first.heatmap,
      frames: ordered.flatMap((chunk) => chunk.heatmap.frames),
    },
  };
}

function mergeProgressPoints(current: EvacuationPoint[], chunks: SimulationPlaybackChunkData[]) {
  const points = new Map(current.map((point) => [point.timeSeconds, point]));
  for (const chunk of chunks) {
    for (const point of chunk.evacuationProgress) points.set(point.timeSeconds, point);
  }
  return [...points.values()].sort((left, right) => left.timeSeconds - right.timeSeconds);
}

export function useSimulationResultChunks(options: Options) {
  const {
    simulationId,
    totalPeople,
    maxDensity,
    currentTimeSeconds,
    chunkDurationSeconds,
    timelineChunkCount,
    heatmapChunkCount,
  } = options;
  const chunkCount = Math.min(timelineChunkCount, heatmapChunkCount);
  const sequence = Math.min(
    Math.max(0, chunkCount - 1),
    Math.floor(currentTimeSeconds / Math.max(chunkDurationSeconds, 0.001)),
  );
  const sequences = useMemo(
    () => timelineChunkWindow(sequence, chunkCount),
    [chunkCount, sequence],
  );
  const sequenceKey = sequences.join(',');
  const cacheRef = useRef(new Map<number, SimulationPlaybackChunkData>());
  // 백그라운드 프리페치가 끝낸 청크를 추적한다. 캐시는 재생 창 이동 시 지워지므로,
  // 이펙트가 다시 실행돼도 이미 받은 청크를 다시 요청하지 않게 하는 기준은 여기다.
  const prefetchedSequencesRef = useRef(new Set<number>());
  const requestVersionRef = useRef(0);
  const [windowData, setWindowData] = useState<PlaybackWindow | null>(null);
  const [progress, setProgress] = useState<EvacuationPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryVersion, setRetryVersion] = useState(0);

  useEffect(() => {
    cacheRef.current.clear();
    prefetchedSequencesRef.current.clear();
    requestVersionRef.current += 1;
    setWindowData(null);
    setProgress([]);
    setError(null);
    setLoading(true);
  }, [simulationId, totalPeople, maxDensity]);

  useEffect(() => {
    const requestVersion = requestVersionRef.current + 1;
    requestVersionRef.current = requestVersion;
    const controller = new AbortController();
    if (chunkCount < 1 || timelineChunkCount !== heatmapChunkCount) {
      setLoading(false);
      setError('타임라인과 히트맵 결과 청크가 올바르게 저장되지 않았습니다.');
      return;
    }
    setLoading(true);
    setError(null);

    const load = async () => {
      const loaded = await Promise.all(
        sequences.map(async (chunkSequence) => {
          const cached = cacheRef.current.get(chunkSequence);
          if (cached) return cached;
          return loadWithRetry(
            () =>
              simulationResultProvider.getPlaybackChunk(
                simulationId,
                chunkSequence,
                totalPeople,
                maxDensity,
                controller.signal,
              ),
            { signal: controller.signal },
          );
        }),
      );
      if (requestVersionRef.current !== requestVersion) return;
      for (const chunk of loaded) cacheRef.current.set(chunk.sequence, chunk);
      const nextWindow = mergeWindow(loaded);
      setWindowData(nextWindow);
      setProgress((current) => mergeProgressPoints(current, loaded));
      const retained = new Set(sequences);
      for (const cachedSequence of cacheRef.current.keys()) {
        if (!retained.has(cachedSequence)) cacheRef.current.delete(cachedSequence);
      }
      setLoading(false);
    };

    void load().catch((loadError: unknown) => {
      if (requestVersionRef.current !== requestVersion) return;
      if (controller.signal.aborted || isCancelledRequest(loadError)) return;
      setLoading(false);
      setError('시뮬레이션 재생 데이터를 불러오지 못했습니다.');
    });
    return () => controller.abort();
  }, [
    chunkCount,
    heatmapChunkCount,
    maxDensity,
    retryVersion,
    sequenceKey,
    sequences,
    simulationId,
    timelineChunkCount,
    totalPeople,
  ]);

  // 재생 창과 별개로 전체 청크를 순서대로 받아 시간별 대피 인원 그래프를 완성한다.
  // 토글로 결과를 전환하면 현재 시점 주변 청크만으로는 그래프 앞부분이 잘려 보이므로,
  // 배경으로 남은 청크의 대피 진행 데이터를 채워 넣는다. 이때 에이전트 프레임·히트맵 같은
  // 렌더링 데이터는 진행률만 추출한 뒤 버린다. 전체를 캐시에 담아두면 긴 시뮬레이션에서
  // 메모리가 청크 수에 비례해 늘어나므로 재요청 방지는 완료 추적 Set으로 충분하다.
  // 실패한 청크는 추적에 남지 않아 다음 실행에서 재시도하고, 주 윈도우와 스크럽 로딩이
  // 필요한 시점에 언제든 다시 가져온다.
  useEffect(() => {
    if (chunkCount < 1 || timelineChunkCount !== heatmapChunkCount) return;
    const controller = new AbortController();
    const run = async () => {
      for (let sequence = 0; sequence < chunkCount; sequence += 1) {
        if (controller.signal.aborted) return;
        if (cacheRef.current.has(sequence) || prefetchedSequencesRef.current.has(sequence)) {
          // 재생 창이 캐시에 담은 청크의 진행률도 이미 병합됐으니 같은 완료 추적으로 기록한다.
          prefetchedSequencesRef.current.add(sequence);
          continue;
        }
        try {
          const data = await loadWithRetry(
            () =>
              simulationResultProvider.getPlaybackChunk(
                simulationId,
                sequence,
                totalPeople,
                maxDensity,
                controller.signal,
              ),
            { signal: controller.signal },
          );
          if (controller.signal.aborted) return;
          prefetchedSequencesRef.current.add(sequence);
          setProgress((current) => mergeProgressPoints(current, [data]));
        } catch {
          // 백그라운드 보조 로딩 실패는 무시한다.
        }
      }
    };
    void run();
    return () => {
      controller.abort();
    };
  }, [
    chunkCount,
    heatmapChunkCount,
    maxDensity,
    retryVersion,
    simulationId,
    timelineChunkCount,
    totalPeople,
  ]);

  return {
    agentFrames: windowData?.agentFrames ?? [],
    heatmap: windowData?.heatmap ?? null,
    evacuationProgress: progress,
    readyForCurrentTime: windowData?.sequences.includes(sequence) ?? false,
    loading,
    error,
    retry: () => setRetryVersion((version) => version + 1),
  };
}
