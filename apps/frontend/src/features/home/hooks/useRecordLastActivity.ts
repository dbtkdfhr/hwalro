import { useCallback } from 'react';
import { homeApi } from '../api/homeApi';
import type { LastActivityType } from '../types/home';

export type LastActivityRecorder = (
  activityType: LastActivityType,
  resourceId: number | null | undefined,
) => void;

/**
 * 마지막 작업 위치를 기록하는 함수를 돌려준다.
 *
 * 화면 진입이 아니라 실제로 작업을 진행한 시점에만 호출해야 한다.
 * 진입만으로 기록하면 이미 다음 단계로 넘어간 사용자가 이전 화면을 잠깐
 * 열어보기만 해도 진행 단계가 되돌아간다.
 *
 * 홈 화면의 "검토 이어가기"를 위한 부가 기능이므로 실패해도 화면을 막지 않는다.
 */
export function useRecordLastActivity(): LastActivityRecorder {
  return useCallback((activityType, resourceId) => {
    if (resourceId == null || Number.isNaN(resourceId)) return;
    void homeApi.recordLastActivity(activityType, resourceId).catch(() => undefined);
  }, []);
}
