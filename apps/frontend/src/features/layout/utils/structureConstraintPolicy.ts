import type { LayoutZone } from '../api/layoutMetadataApi';
import { can } from '../../auth/capabilities';

/**
 * 이 구조물의 배치 제약을 수정할 수 있는가.
 *
 * 화면 표시 판단일 뿐이며 실제 차단은 서버가 한다(같은 규칙이 `LayoutMetadataService`에도 있다).
 * - 구역 관리 권한이 있으면 모든 구조물을 수정할 수 있다.
 * - 그렇지 않으면 자기에게 배정된 구역의 구조물만 수정할 수 있다.
 * - 어떤 구역에도 속하지 않은 공용 구조물은 직원이 수정할 수 없다.
 */
export function canEditStructureConstraints(
  roles: readonly string[] | undefined,
  userId: number | null,
  zone: LayoutZone | null,
): boolean {
  if (can(roles, 'zones.manage')) {
    return true;
  }
  if (!can(roles, 'zones.assigned') || zone === null || userId === null) {
    return false;
  }
  return zone.assignedUserId === userId;
}
