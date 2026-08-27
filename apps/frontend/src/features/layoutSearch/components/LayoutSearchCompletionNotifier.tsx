import { useQuery } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { CompletionToast } from '../../../components/notifications/CompletionToast';
import {
  layoutSearchApi,
  type LayoutSearchMonitorItem,
  type SearchStatus,
} from '../api/layoutSearchApi';

const POLL_INTERVAL_MS = 3000;

export function findNewlyCompletedLayoutSearches(
  previousStatuses: ReadonlyMap<number, SearchStatus>,
  searches: LayoutSearchMonitorItem[],
): LayoutSearchMonitorItem[] {
  return searches.filter(
    (search) =>
      search.status === 'COMPLETED' && previousStatuses.get(search.searchId) !== 'COMPLETED',
  );
}

function LayoutSearchCompletionNotifier() {
  const [toasts, setToasts] = useState<LayoutSearchMonitorItem[]>([]);
  const previousStatusesRef = useRef<Map<number, SearchStatus> | null>(null);
  const notifiedIdsRef = useRef(new Set<number>());
  const query = useQuery({
    queryKey: ['layout-searches', 'completion-monitor'],
    queryFn: layoutSearchApi.listMonitor,
    refetchInterval: POLL_INTERVAL_MS,
  });

  const dismiss = useCallback((searchId: number) => {
    setToasts((current) => current.filter((toast) => toast.searchId !== searchId));
  }, []);

  useEffect(() => {
    if (!query.data) return;

    const currentStatuses = new Map<number, SearchStatus>(
      query.data.map((search) => [search.searchId, search.status]),
    );
    const previousStatuses = previousStatusesRef.current;
    previousStatusesRef.current = currentStatuses;
    if (!previousStatuses) return;

    const completed = findNewlyCompletedLayoutSearches(previousStatuses, query.data).filter(
      (search) => !notifiedIdsRef.current.has(search.searchId),
    );
    if (completed.length === 0) return;

    completed.forEach((search) => notifiedIdsRef.current.add(search.searchId));
    setToasts((current) => [...current, ...completed]);
  }, [query.data]);

  if (toasts.length === 0) return null;

  return (
    <>
      {toasts.map((search) => (
        <CompletionToast
          key={search.searchId}
          id={search.searchId}
          title="배치 개선안 후보 생성이 완료되었습니다"
          description={`${search.title} · 개선안 탐색 #${search.searchId}`}
          dismissLabel="배치 개선안 완료 알림 닫기"
          onDismiss={dismiss}
          action={
            <Link
              to={`/simulations/${search.baselineSimulationId}/layout-search`}
              onClick={() => dismiss(search.searchId)}
              className="mt-3 inline-flex text-sm font-bold text-primary hover:underline focus-visible:rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
            >
              개선안 보기
            </Link>
          }
        />
      ))}
    </>
  );
}

export default LayoutSearchCompletionNotifier;
