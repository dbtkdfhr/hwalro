import { apiClient } from '../../../api/client';

/** auth-service의 UserSummaryResponse record와 필드명이 같아야 한다. */
interface UserSummaryResponse {
  id: number;
  name: string;
}

/**
 * 표시용 사용자 이름 조회.
 * 운영 담당자는 본인 외 조회 시 403이 되므로 호출부는 실패를 허용해야 한다.
 */
export const userNameApi = {
  listNames: (ids: number[]) =>
    apiClient
      .get<UserSummaryResponse[]>('/api/auth/users', { params: { ids: ids.join(',') } })
      .then((res) => new Map(res.data.map((user) => [user.id, user.name]))),
};
