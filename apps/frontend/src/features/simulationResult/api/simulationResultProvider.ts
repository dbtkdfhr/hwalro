import axios from 'axios';
import { apiClient } from '../../../api/client';
import { simulationApi } from '../../simulations/api/simulationApi';
import type {
  ComparableSimulationPage,
  SimulationResultProvider,
  SimulationResultSummaryViewModel,
} from '../types';
import { convertPlaybackChunks } from '../utils/resultChunks';

interface SegmentResponse {
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation?: number;
}

interface SimulationResultSummaryResponse {
  simulationId: number;
  simulationResultId: number;
  title: string;
  subtitle: string;
  durationSeconds: number;
  totalPeople: number;
  maxDensity: number;
  densityThreshold: number;
  drawing: {
    name: string;
    width: number;
    height: number;
    outsideBoundary: Array<{ x: number; y: number }>;
    walls: SegmentResponse[];
    exits: SegmentResponse[];
    pillars: SegmentResponse[];
    fabrics: SegmentResponse[];
    layoutTexts: Array<{ text: string; x: number; y: number }>;
  };
  hazardZones: Array<{
    id: number;
    centerX: number;
    centerY: number;
    radius: number;
  }>;
  bottlenecks: Array<{
    id: number;
    order: number;
    name: string;
    startTimeSeconds: number;
    endTimeSeconds: number;
    peakDensity: number;
    thresholdValue: number;
    geometry: { x: number; y: number; width: number; height: number };
  }>;
}

function toSummaryViewModel(
  response: SimulationResultSummaryResponse,
): SimulationResultSummaryViewModel {
  return {
    ...response,
    simulationId: String(response.simulationId),
    hazardZones: response.hazardZones ?? [],
  };
}

export const simulationResultProvider: SimulationResultProvider = {
  async getSummary(simulationId) {
    try {
      const response = await apiClient.get<SimulationResultSummaryResponse>(
        `/api/simulations/${simulationId}/result`,
      );
      return toSummaryViewModel(response.data);
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) return null;
      throw error;
    }
  },

  async getComparableSimulations(simulationId, page, size) {
    const response = await apiClient.get<ComparableSimulationPage>(
      `/api/simulations/${simulationId}/result/comparable-simulations`,
      { params: { page, size } },
    );
    return response.data;
  },

  async getPlaybackChunk(simulationId, sequence, totalPeople, maxDensity) {
    const [timeline, heatmap] = await Promise.all([
      simulationApi.getTimelineChunk(simulationId, sequence),
      simulationApi.getHeatmapChunk(simulationId, sequence),
    ]);
    return convertPlaybackChunks(timeline, heatmap, totalPeople, maxDensity);
  },
};
