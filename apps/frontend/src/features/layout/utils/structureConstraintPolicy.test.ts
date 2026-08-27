import { describe, expect, it } from 'vitest';
import type { LayoutZone } from '../api/layoutMetadataApi';
import { canEditStructureConstraints } from './structureConstraintPolicy';

function zone(assignedUserId: number | null): LayoutZone {
  return {
    zoneId: 30,
    name: '구역',
    zoneType: 'WORK',
    rect: { x: 0, y: 0, width: 10, height: 10 },
    assignedUserId,
    defaultExitId: null,
    displayOrder: 0,
    members: [],
  };
}

describe('canEditStructureConstraints', () => {
  it('구역 관리 권한이 있으면 공용 구조물도 수정할 수 있다', () => {
    expect(canEditStructureConstraints(['OPERATOR'], 7, zone(9))).toBe(true);
    expect(canEditStructureConstraints(['OPERATOR'], 7, null)).toBe(true);
  });

  it('배정된 직원은 자기 구역 구조물만 수정할 수 있다', () => {
    expect(canEditStructureConstraints(['GENERAL_EMPLOYEE'], 9, zone(9))).toBe(true);
  });

  it('다른 구역의 구조물은 수정할 수 없다', () => {
    expect(canEditStructureConstraints(['GENERAL_EMPLOYEE'], 9, zone(99))).toBe(false);
  });

  it('공용 구조물은 직원이 수정할 수 없다', () => {
    expect(canEditStructureConstraints(['GENERAL_EMPLOYEE'], 9, null)).toBe(false);
  });
});
