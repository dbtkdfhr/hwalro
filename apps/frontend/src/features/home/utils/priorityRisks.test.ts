import { describe, expect, it } from 'vitest';
import { countPriorityRisks, filterPriorityRisks, selectPriorityRisks } from './priorityRisks';
import type { Risk } from '../../risks/types/risks';

function risk(overrides: Partial<Risk> & Pick<Risk, 'id'>): Risk {
  return {
    layoutId: 1,
    layoutVersionId: null,
    assigneeId: null,
    assigneeName: null,
    title: `위험 ${overrides.id}`,
    description: null,
    startX: null,
    startY: null,
    endX: null,
    endY: null,
    severity: '높음',
    status: '조치 중',
    attachedLaws: [],
    createdAt: '2026-08-01T09:00:00',
    layoutTitle: null,
    ...overrides,
  };
}

describe('selectPriorityRisks and countPriorityRisks', () => {
  it('완료된 항목은 제외한다', () => {
    const result = selectPriorityRisks([risk({ id: 1, status: '완료' }), risk({ id: 2 })]);

    expect(result.map((item) => item.id)).toEqual([2]);
  });

  it('심각도가 높은 순(높음 > 보통 > 낮음)으로 우선 정렬한다', () => {
    const result = selectPriorityRisks([
      risk({ id: 1, severity: '낮음', createdAt: '2026-08-10T09:00:00' }),
      risk({ id: 2, severity: '높음', createdAt: '2026-08-01T09:00:00' }),
      risk({ id: 3, severity: '보통', createdAt: '2026-08-05T09:00:00' }),
    ]);

    expect(result.map((item) => item.id)).toEqual([2, 3, 1]);
  });

  it('동일한 심각도 내에서는 최신 생성순으로 정렬한다', () => {
    const result = selectPriorityRisks([
      risk({ id: 1, severity: '높음', createdAt: '2026-08-01T09:00:00' }),
      risk({ id: 2, severity: '높음', createdAt: '2026-08-09T09:00:00' }),
      risk({ id: 3, severity: '높음', createdAt: '2026-08-05T09:00:00' }),
    ]);

    expect(result.map((item) => item.id)).toEqual([2, 3, 1]);
  });

  it('심각도와 생성 시각이 같으면 id 내림차순으로 정렬한다', () => {
    const result = selectPriorityRisks([risk({ id: 1 }), risk({ id: 3 }), risk({ id: 2 })]);

    expect(result.map((item) => item.id)).toEqual([3, 2, 1]);
  });

  it('기본적으로 3건까지만 반환한다', () => {
    const result = selectPriorityRisks([1, 2, 3, 4, 5].map((id) => risk({ id })));

    expect(result).toHaveLength(3);
  });

  it('입력 배열을 변형하지 않는다', () => {
    const input = [risk({ id: 1 }), risk({ id: 2 })];

    selectPriorityRisks(input);

    expect(input.map((item) => item.id)).toEqual([1, 2]);
  });

  it('countPriorityRisks는 완료되지 않은 모든 심각도의 주의 항목 전체 개수를 반환한다', () => {
    const list = [
      risk({ id: 1, severity: '높음' }),
      risk({ id: 2, severity: '보통' }),
      risk({ id: 3, severity: '낮음' }),
      risk({ id: 4, severity: '높음' }),
      risk({ id: 5, status: '완료', severity: '높음' }),
    ];

    expect(countPriorityRisks(list)).toBe(4);
    expect(filterPriorityRisks(list)).toHaveLength(4);
    expect(selectPriorityRisks(list)).toHaveLength(3);
  });
});
