import { useCallback, useEffect, useRef, useState } from 'react';
import { AxiosError } from 'axios';
import { getSimulationErrorMessage } from '../../simulations/utils/getSimulationErrorMessage';
import {
  layoutSearchApi,
  emptyConstraints,
  type LayoutSearch,
  type PreparedSimulation,
  type SearchConstraints,
  type SearchStatus,
} from '../api/layoutSearchApi';

export const ACTIVE_SEARCH_STATUSES: SearchStatus[] = [
  'PENDING',
  'DIAGNOSING',
  'GENERATING',
  'VERIFYING',
];

export const POLL_INTERVAL_MS = 5000;

export function isActiveSearchStatus(status: SearchStatus) {
  return ACTIVE_SEARCH_STATUSES.includes(status);
}

export function isNotFoundError(error: unknown) {
  return error instanceof AxiosError && error.response?.status === 404;
}

export function useLayoutSearch(simulationId: number) {
  const [search, setSearch] = useState<LayoutSearch | null>(null);
  const [hasSearch, setHasSearch] = useState(false);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [preparingCandidateIds, setPreparingCandidateIds] = useState<ReadonlySet<number>>(
    () => new Set(),
  );
  const preparingCandidateIdsRef = useRef(new Set<number>());
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [constraints, setConstraints] = useState<SearchConstraints>(() => emptyConstraints());

  const refresh = useCallback(async () => {
    try {
      const latest = await layoutSearchApi.latest(simulationId);
      setSearch(latest);
      setHasSearch(true);
      setErrorMessage(null);
    } catch (error) {
      if (!isNotFoundError(error)) {
        setErrorMessage(getSimulationErrorMessage(error));
      }
    } finally {
      setLoading(false);
    }
  }, [simulationId]);

  const initialize = useCallback(async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const latest = await layoutSearchApi.latest(simulationId);
      setSearch(latest);
      setHasSearch(true);
    } catch (error) {
      if (isNotFoundError(error)) {
        setHasSearch(false);
      } else {
        setErrorMessage(getSimulationErrorMessage(error));
      }
    } finally {
      setLoading(false);
    }
  }, [simulationId]);

  useEffect(() => {
    void initialize();
  }, [initialize]);

  const active = search !== null && isActiveSearchStatus(search.status);

  useEffect(() => {
    if (!active) {
      return;
    }
    let disposed = false;
    let timer = window.setTimeout(function poll() {
      void refresh().finally(() => {
        if (!disposed) {
          timer = window.setTimeout(poll, POLL_INTERVAL_MS);
        }
      });
    }, POLL_INTERVAL_MS);
    return () => {
      disposed = true;
      window.clearTimeout(timer);
    };
  }, [active, refresh]);

  const start = useCallback(
    async (nextConstraints?: SearchConstraints, verify = false): Promise<boolean> => {
      setStarting(true);
      setErrorMessage(null);
      try {
        const applied = nextConstraints ?? constraints;
        await layoutSearchApi.start(simulationId, applied, verify);
        await refresh();
        return true;
      } catch (error) {
        setErrorMessage(getSimulationErrorMessage(error));
        return false;
      } finally {
        setStarting(false);
      }
    },
    [simulationId, constraints, refresh],
  );

  const updateConstraints = useCallback(
    (updater: (current: SearchConstraints) => SearchConstraints) => {
      setConstraints((current) => updater(current));
    },
    [],
  );

  const resetToSetup = useCallback(() => {
    setSearch(null);
    setHasSearch(false);
    setErrorMessage(null);
    setCancelling(false);
  }, []);

  const rejectCandidate = useCallback(
    async (candidateId: number): Promise<boolean> => {
      if (!search) {
        return false;
      }
      const candidate = search.improvedCandidates.find(
        (entry) => entry.candidateId === candidateId,
      );
      if (!candidate) {
        return false;
      }
      const fabricIds = candidate.changeSet.ops.map((op) => op.fabricId);
      const next: SearchConstraints = {
        ...constraints,
        moveRadii: { ...constraints.moveRadii },
      };
      fabricIds.forEach((fabricId) => {
        next.moveRadii[fabricId] = 0;
      });
      setConstraints(next);
      return start(next);
    },
    [constraints, search, start],
  );

  const cancel = useCallback(async () => {
    if (!search) {
      return;
    }
    setCancelling(true);
    setErrorMessage(null);
    try {
      await layoutSearchApi.cancel(search.searchId);
      await refresh();
    } catch (error) {
      setErrorMessage(getSimulationErrorMessage(error));
    } finally {
      setCancelling(false);
    }
  }, [search, refresh]);

  const prepareSimulation = useCallback(
    async (candidateId: number): Promise<PreparedSimulation | null> => {
      if (!search) {
        return null;
      }
      if (preparingCandidateIdsRef.current.has(candidateId)) {
        return null;
      }
      preparingCandidateIdsRef.current.add(candidateId);
      setPreparingCandidateIds(new Set(preparingCandidateIdsRef.current));
      setErrorMessage(null);
      try {
        const result = await layoutSearchApi.prepareSimulation(search.searchId, candidateId);
        setSearch((current) =>
          current === null
            ? current
            : {
                ...current,
                improvedCandidates: current.improvedCandidates.map((candidate) =>
                  candidate.candidateId === candidateId
                    ? { ...candidate, preparedSimulation: result }
                    : candidate,
                ),
              },
        );
        await refresh();
        return result;
      } catch (error) {
        setErrorMessage(getSimulationErrorMessage(error));
        return null;
      } finally {
        preparingCandidateIdsRef.current.delete(candidateId);
        setPreparingCandidateIds(new Set(preparingCandidateIdsRef.current));
      }
    },
    [search, refresh],
  );

  return {
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
  };
}
