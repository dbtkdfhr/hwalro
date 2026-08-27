import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { useAuth } from '../../auth/context/AuthContext';
import { userNameApi } from '../../home/api/userNameApi';
import { drawingApi } from '../api/drawingApi';

export function useDrawingList(page: number, size: number, query?: string) {
  const { user } = useAuth();
  const userId = user?.id ?? 'unknown';

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['drawings', userId, page, size, query],
    queryFn: () => drawingApi.list(page, size, query),
  });

  // 등록자 표시 이름을 한 번에 조회한다.
  const creatorIds = useMemo(() => {
    const ids = new Set<number>();
    (data?.items ?? []).forEach((item) => ids.add(item.createdBy));
    return [...ids].sort((a, b) => a - b);
  }, [data]);

  const namesQuery = useQuery({
    queryKey: ['drawings', 'user-names', creatorIds],
    queryFn: () => userNameApi.listNames(creatorIds),
    enabled: creatorIds.length > 0,
    // 운영 담당자는 본인 외 조회 시 403이 되므로 재시도하지 않고 이름 없이 표시한다.
    retry: false,
  });

  return {
    items: data?.items ?? [],
    totalCount: data?.totalCount ?? 0,
    isPending,
    isError,
    error,
    nameById: namesQuery.data,
  };
}
