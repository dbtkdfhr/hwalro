import { useEffect, useMemo, useRef, useState } from 'react';
import { timelineChunkWindow } from '../../simulations/utils/timeline';
import { simulationResultProvider } from '../api/simulationResultProvider';
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
  const requestVersionRef = useRef(0);
  const [windowData, setWindowData] = useState<PlaybackWindow | null>(null);
  const [progress, setProgress] = useState<EvacuationPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryVersion, setRetryVersion] = useState(0);

  useEffect(() => {
    cacheRef.current.clear();
    requestVersionRef.current += 1;
    setWindowData(null);
    setProgress([]);
    setError(null);
    setLoading(true);
  }, [simulationId, totalPeople, maxDensity]);

  useEffect(() => {
    const requestVersion = requestVersionRef.current + 1;
    requestVersionRef.current = requestVersion;
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
          return simulationResultProvider.getPlaybackChunk(
            simulationId,
            chunkSequence,
            totalPeople,
            maxDensity,
          );
        }),
      );
      if (requestVersionRef.current !== requestVersion) return;
      for (const chunk of loaded) cacheRef.current.set(chunk.sequence, chunk);
      const nextWindow = mergeWindow(loaded);
      setWindowData(nextWindow);
      setProgress((current) => {
        const points = new Map(current.map((point) => [point.timeSeconds, point]));
        for (const chunk of loaded) {
          for (const point of chunk.evacuationProgress) points.set(point.timeSeconds, point);
        }
        return [...points.values()].sort((left, right) => left.timeSeconds - right.timeSeconds);
      });
      const retained = new Set(sequences);
      for (const cachedSequence of cacheRef.current.keys()) {
        if (!retained.has(cachedSequence)) cacheRef.current.delete(cachedSequence);
      }
      setLoading(false);
    };

    void load().catch(() => {
      if (requestVersionRef.current !== requestVersion) return;
      setLoading(false);
      setError('시뮬레이션 재생 데이터를 불러오지 못했습니다.');
    });
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
