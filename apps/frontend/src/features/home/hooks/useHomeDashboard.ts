import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { useMemo } from 'react';
import { useAuth } from '../../auth/context/AuthContext';
import { drawingApi } from '../../drawings/api/drawingApi';
import { getDrawingErrorMessage } from '../../drawings/utils/getDrawingErrorMessage';
import { riskApi } from '../../risks/api/riskApi';
import { getSimulationErrorMessage } from '../../simulations/utils/getSimulationErrorMessage';
import { homeApi } from '../api/homeApi';
import { userNameApi } from '../api/userNameApi';
import type { ActiveReview, PriorityRiskItem, SimulationWorkSummary } from '../types/home';
import { countPriorityRisks, selectPriorityRisks } from '../utils/priorityRisks';
import {
  activityHasSimulation,
  currentStageLabel,
  resumePath,
  reviewSteps,
} from '../utils/reviewProgress';

const RISK_SCAN_SIZE = 20;

function resolveIfExists<T>(request: Promise<T>): Promise<T | null> {
  return request.catch((error) => {
    if (axios.isAxiosError(error) && error.response?.status === 404) return null;
    throw error;
  });
}

export function useHomeDashboard() {
  const { user } = useAuth();
  const userId = user?.id ?? 'unknown';

  const lastActivityQuery = useQuery({
    queryKey: ['home', 'last-activity', userId],
    queryFn: homeApi.getLastActivity,
  });

  const summaryQuery = useQuery({
    queryKey: ['home', 'summary', userId],
    queryFn: homeApi.getWorkSummary,
  });

  const risksQuery = useQuery({
    queryKey: ['home', 'priority-risks'],
    queryFn: () => riskApi.list(1, RISK_SCAN_SIZE),
  });

  const activity = lastActivityQuery.data ?? null;
  const simulationPointerId =
    activity && activityHasSimulation(activity.activityType) ? activity.resourceId : null;
  const drawingPointerId =
    activity && !activityHasSimulation(activity.activityType) ? activity.resourceId : null;

  const pointedSimulationQuery = useQuery({
    queryKey: ['home', 'pointed-simulation', simulationPointerId],
    queryFn: () => resolveIfExists(homeApi.getSimulationOverview(simulationPointerId as number)),
    enabled: simulationPointerId != null,
  });

  const pointedDrawingQuery = useQuery({
    queryKey: ['home', 'pointed-drawing', drawingPointerId],
    queryFn: () => resolveIfExists(drawingApi.get(drawingPointerId as number)),
    enabled: drawingPointerId != null,
  });

  const priorityRisks = useMemo(
    () => selectPriorityRisks(risksQuery.data?.items ?? []),
    [risksQuery.data],
  );

  // 우선 확인할 위험 항목의 담당자 이름을 한 번에 조회한다.
  const assigneeIds = useMemo(() => {
    const ids = new Set<number>();
    priorityRisks.forEach((risk) => {
      if (risk.assigneeId != null) ids.add(risk.assigneeId);
    });
    return [...ids].sort((a, b) => a - b);
  }, [priorityRisks]);

  const namesQuery = useQuery({
    queryKey: ['home', 'user-names', assigneeIds],
    queryFn: () => userNameApi.listNames(assigneeIds),
    enabled: assigneeIds.length > 0,
    // 운영 담당자는 본인 외 조회 시 403이므로 재시도하지 않고 이름 없이 표시한다.
    retry: false,
  });
  const nameById = namesQuery.data;

  const activeReview = useMemo<ActiveReview | null>(() => {
    if (!activity) return null;

    if (drawingPointerId != null) {
      const drawing = pointedDrawingQuery.data;
      if (!drawing) return null;
      const progress = { status: null, totalPeople: 0, analysisOpened: false };
      return {
        title: drawing.title,
        resumePath: resumePath(activity),
        subtitle: `도면 #${drawing.id} · 버전 ${drawing.layoutVersionNumber}`,
        occurredAt: activity.occurredAt,
        currentStageLabel: currentStageLabel(progress),
        steps: reviewSteps(progress),
      };
    }

    const simulation = pointedSimulationQuery.data;
    if (!simulation) return null;
    const progress = {
      status: simulation.status,
      totalPeople: simulation.totalPeople,
      analysisOpened: activity.activityType === 'SIMULATION_RESULT',
    };
    return {
      title: simulation.title || simulation.layoutTitle,
      resumePath: resumePath(activity),
      subtitle: `도면 #${simulation.layoutId} · 버전 ${simulation.layoutVersionNumber} · 시뮬레이션 #${simulation.id}`,
      occurredAt: activity.occurredAt,
      currentStageLabel: currentStageLabel(progress),
      steps: reviewSteps(progress),
    };
  }, [activity, drawingPointerId, pointedDrawingQuery.data, pointedSimulationQuery.data]);

  const priorityRiskItems = useMemo<PriorityRiskItem[]>(
    () =>
      priorityRisks.map((risk) => ({
        id: risk.id,
        title: risk.title,
        severity: risk.severity,
        status: risk.status,
        assigneeId: risk.assigneeId,
        assigneeName: risk.assigneeId == null ? null : (nameById?.get(risk.assigneeId) ?? null),
      })),
    [priorityRisks, nameById],
  );

  const workSummary: SimulationWorkSummary = summaryQuery.data ?? {
    inProgressCount: 0,
    completedThisWeekCount: 0,
  };

  const isActiveReviewPending =
    lastActivityQuery.isPending ||
    (simulationPointerId != null && pointedSimulationQuery.isPending) ||
    (drawingPointerId != null && pointedDrawingQuery.isPending);

  const activeReviewErrorMessage = lastActivityQuery.isError
    ? '마지막 작업 위치를 불러오지 못했습니다.'
    : drawingPointerId != null && pointedDrawingQuery.isError
      ? getDrawingErrorMessage(pointedDrawingQuery.error)
      : pointedSimulationQuery.isError
        ? getSimulationErrorMessage(pointedSimulationQuery.error)
        : '';

  const totalPriorityCount = useMemo(
    () => (risksQuery.data?.items ? countPriorityRisks(risksQuery.data.items) : 0),
    [risksQuery.data],
  );

  return {
    activeReview: {
      data: activeReview,
      isPending: isActiveReviewPending,
      isError:
        lastActivityQuery.isError || pointedSimulationQuery.isError || pointedDrawingQuery.isError,
      errorMessage: activeReviewErrorMessage,
      refetch: () => {
        void lastActivityQuery.refetch();
        void pointedSimulationQuery.refetch();
        void pointedDrawingQuery.refetch();
      },
    },
    workSummary: {
      data: workSummary,
      isPending: summaryQuery.isPending,
      isError: summaryQuery.isError,
      error: summaryQuery.error,
      refetch: () => void summaryQuery.refetch(),
    },
    priorityRisks: {
      data: priorityRiskItems,
      totalCount: totalPriorityCount,
      isPending: risksQuery.isPending,
      isError: risksQuery.isError,
      error: risksQuery.error,
      refetch: () => void risksQuery.refetch(),
    },
  };
}
