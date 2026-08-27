import { useCallback, useEffect, useState } from 'react';
import { getSimulationErrorMessage } from '../../simulations/utils/getSimulationErrorMessage';
import { layoutSearchApi, type LayoutSearch } from '../api/layoutSearchApi';
import { isActiveSearchStatus, isNotFoundError, POLL_INTERVAL_MS } from './useLayoutSearch';

export type LayoutSearchFeeds = Record<number, LayoutSearch | null>;

export function useLayoutSearchFeeds(simulationIds: readonly number[]) {
  const [feeds, setFeeds] = useState<LayoutSearchFeeds>({});
  const [error, setError] = useState<string | null>(null);
  const idsKey = simulationIds.join(',');

  const refresh = useCallback(async () => {
    setError(null);
    const ids = idsKey.split(',').filter(Boolean).map(Number);
    if (ids.length === 0) {
      setFeeds({});
      return;
    }
    const next: LayoutSearchFeeds = {};
    await Promise.all(
      ids.map(async (simulationId) => {
        try {
          next[simulationId] = await layoutSearchApi.latest(simulationId);
        } catch (caught) {
          if (isNotFoundError(caught)) {
            next[simulationId] = null;
          } else {
            next[simulationId] = null;
            setError(getSimulationErrorMessage(caught));
          }
        }
      }),
    );
    setFeeds(next);
  }, [idsKey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const active = Object.values(feeds).some((search) => {
    if (search === null) return false;
    if (isActiveSearchStatus(search.status)) return true;
    return [...search.improvedCandidates, ...search.rejectedCandidates].some(
      (c) =>
        c.status === 'RUNNING' ||
        c.preparedSimulation?.status === 'REQUESTED' ||
        c.preparedSimulation?.status === 'RUNNING',
    );
  });

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

  return { feeds, error, active, refresh };
}
