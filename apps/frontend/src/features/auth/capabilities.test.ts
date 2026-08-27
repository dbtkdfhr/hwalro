import { describe, expect, it } from 'vitest';
import { can, capabilitiesOf, homeRouteFor } from './capabilities';

describe('capabilities', () => {
  it('매장 직원은 담당 구역과 체크리스트 권한만 갖는다', () => {
    const roles = ['GENERAL_EMPLOYEE'];

    expect(can(roles, 'zones.assigned')).toBe(true);
    expect(can(roles, 'checklists')).toBe(true);
    expect(can(roles, 'checklists.manage')).toBe(false);
    expect(can(roles, 'drawings.view')).toBe(false);
    expect(can(roles, 'simulations')).toBe(false);
    expect(can(roles, 'drawings.manage')).toBe(false);
    expect(can(roles, 'reports')).toBe(false);
    expect(can(roles, 'risks')).toBe(false);
    expect(can(roles, 'systemManagement')).toBe(false);
  });

  it('복합 역할은 넓은 권한이 이긴다', () => {
    const roles = ['GENERAL_EMPLOYEE', 'OPERATOR'];

    expect(can(roles, 'simulations')).toBe(true);
    expect(can(roles, 'drawings.manage')).toBe(true);
    // 직원 권한도 함께 유지된다 - 합집합이므로.
    expect(can(roles, 'zones.assigned')).toBe(true);
  });

  it('관리자만 시스템 관리를 갖는다', () => {
    expect(can(['ADMIN'], 'systemManagement')).toBe(true);
    expect(can(['OPERATOR'], 'systemManagement')).toBe(false);
    expect(can(['SAFETY_REVIEWER'], 'systemManagement')).toBe(false);
  });

  it('체크리스트 템플릿 관리는 관리자와 안전 검토자만 갖는다', () => {
    expect(can(['ADMIN'], 'checklists.manage')).toBe(true);
    expect(can(['SAFETY_REVIEWER'], 'checklists.manage')).toBe(true);
    expect(can(['OPERATOR'], 'checklists.manage')).toBe(false);
    expect(can(['GENERAL_EMPLOYEE'], 'checklists.manage')).toBe(false);
  });

  it('알 수 없는 역할과 미로그인은 아무 권한도 주지 않는다', () => {
    expect(capabilitiesOf(['SOMETHING_ELSE']).size).toBe(0);
    expect(capabilitiesOf(undefined).size).toBe(0);
    expect(can(undefined, 'drawings.view')).toBe(false);
  });

  it('업무 화면을 못 보는 사용자는 담당 구역으로 보낸다', () => {
    expect(homeRouteFor(['GENERAL_EMPLOYEE'])).toBe('/my-zones');
    expect(homeRouteFor(['OPERATOR'])).toBe('/');
    expect(homeRouteFor(['GENERAL_EMPLOYEE', 'ADMIN'])).toBe('/');
  });
});
