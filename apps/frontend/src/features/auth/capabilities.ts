/**
 * 역할 → 화면 권한 매핑을 한 곳에 모은다.
 *
 * 여기는 **UX 경계일 뿐 보안 경계가 아니다.** 모든 인가는 서버가 강제하며, 이 파일은 볼 수 없는 메뉴를 감추고
 * 쓸모없는 폴링을 끄는 용도다.
 *
 * 권한은 역할별 목록의 합집합이다. 따라서 여러 역할을 가진 사용자는 자동으로 넓은 쪽을 갖는다.
 */
export type Capability =
  | 'drawings.view'
  | 'drawings.manage'
  | 'simulations'
  | 'reports'
  | 'risks'
  | 'checklists'
  | 'checklists.manage'
  | 'regulations'
  | 'systemManagement'
  | 'zones.manage'
  | 'zones.assigned';

const PRIVILEGED: readonly Capability[] = [
  'drawings.view',
  'drawings.manage',
  'simulations',
  'reports',
  'risks',
  'checklists',
  'regulations',
  'zones.manage',
];

const ROLE_CAPABILITIES: Record<string, readonly Capability[]> = {
  ADMIN: [...PRIVILEGED, 'checklists.manage', 'systemManagement'],
  OPERATOR: PRIVILEGED,
  SAFETY_REVIEWER: [...PRIVILEGED, 'checklists.manage'],
  GENERAL_EMPLOYEE: ['checklists', 'zones.assigned'],
};

export function capabilitiesOf(roles: readonly string[] | undefined): ReadonlySet<Capability> {
  const capabilities = new Set<Capability>();
  for (const role of roles ?? []) {
    for (const capability of ROLE_CAPABILITIES[role] ?? []) {
      capabilities.add(capability);
    }
  }
  return capabilities;
}

export function can(roles: readonly string[] | undefined, capability: Capability): boolean {
  return capabilitiesOf(roles).has(capability);
}

/** 로그인 직후 보낼 곳. 업무 화면을 볼 수 없는 사용자는 담당 구역으로 보낸다. */
export function homeRouteFor(roles: readonly string[] | undefined): string {
  return can(roles, 'simulations') ? '/' : '/my-zones';
}
