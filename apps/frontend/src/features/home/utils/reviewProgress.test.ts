import { describe, expect, it } from 'vitest';
import {
  currentStageLabel,
  resumePath,
  reviewSteps,
  type ReviewProgressInput,
} from './reviewProgress';
import type { LastActivity } from '../types/home';

function activity(activityType: LastActivity['activityType'], resourceId: number): LastActivity {
  return { activityType, resourceId, occurredAt: '2026-08-11T10:24:00' };
}

function progress(overrides: Partial<ReviewProgressInput> = {}): ReviewProgressInput {
  return { status: null, totalPeople: 0, analysisOpened: false, ...overrides };
}

describe('resumePath', () => {
  it('도면 편집은 배치 편집기로 이동한다', () => {
    expect(resumePath(activity('LAYOUT_EDIT', 12))).toBe('/layout/12');
  });

  it('시뮬레이션 설정은 설정 화면으로 이동한다', () => {
    expect(resumePath(activity('SIMULATION_SETUP', 34))).toBe('/simulations/34/setup');
  });

  it('시뮬레이션 결과는 결과 분석 화면으로 이동한다', () => {
    expect(resumePath(activity('SIMULATION_RESULT', 56))).toBe('/simulations/56/results');
  });
});

describe('reviewSteps', () => {
  it('스텝 라벨은 SCENARIO.md의 4단계 순서를 따른다', () => {
    expect(reviewSteps(progress()).map((step) => step.label)).toEqual([
      '도면 배치',
      '시뮬레이션 배치',
      '시뮬레이션 설정',
      '결과 분석',
    ]);
  });

  it('시뮬레이션이 없으면 도면 배치가 진행 중이다', () => {
    expect(reviewSteps(progress()).map((step) => step.state)).toEqual([
      'current',
      'upcoming',
      'upcoming',
      'upcoming',
    ]);
  });

  it('에이전트를 배치하기 전 DRAFT는 시뮬레이션 배치 단계다', () => {
    const steps = reviewSteps(progress({ status: 'DRAFT', totalPeople: 0 }));

    expect(steps.map((step) => step.state)).toEqual(['done', 'current', 'upcoming', 'upcoming']);
  });

  it('에이전트를 배치한 DRAFT는 시뮬레이션 설정 단계다', () => {
    const steps = reviewSteps(progress({ status: 'DRAFT', totalPeople: 12 }));

    expect(steps.map((step) => step.state)).toEqual(['done', 'done', 'current', 'upcoming']);
  });

  it.each(['REQUESTED', 'RUNNING', 'FAILED', 'CANCELLED'] as const)(
    '%s는 결과 분석 단계에 있다',
    (status) => {
      expect(reviewSteps(progress({ status, totalPeople: 12 })).map((step) => step.state)).toEqual([
        'done',
        'done',
        'done',
        'current',
      ]);
    },
  );

  it('완료했지만 결과를 아직 열지 않았으면 결과 분석이 진행 중이다', () => {
    const steps = reviewSteps(progress({ status: 'COMPLETED', totalPeople: 12 }));

    expect(steps[3].state).toBe('current');
  });

  it('결과 분석 화면을 열었으면 결과 분석까지 완료 표시한다', () => {
    const steps = reviewSteps(
      progress({ status: 'COMPLETED', totalPeople: 12, analysisOpened: true }),
    );

    expect(steps.map((step) => step.state)).toEqual(['done', 'done', 'done', 'done']);
  });
});

describe('currentStageLabel', () => {
  it('시뮬레이션이 없으면 도면 배치 중', () => {
    expect(currentStageLabel(progress())).toBe('도면 배치 중');
  });

  it('에이전트 배치 전 DRAFT는 시뮬레이션 배치 중', () => {
    expect(currentStageLabel(progress({ status: 'DRAFT', totalPeople: 0 }))).toBe(
      '시뮬레이션 배치 중',
    );
  });

  it('에이전트 배치 후 DRAFT는 시뮬레이션 설정 중', () => {
    expect(currentStageLabel(progress({ status: 'DRAFT', totalPeople: 12 }))).toBe(
      '시뮬레이션 설정 중',
    );
  });

  it.each(['REQUESTED', 'RUNNING'] as const)('%s는 시뮬레이션 실행 중', (status) => {
    expect(currentStageLabel(progress({ status, totalPeople: 12 }))).toBe('시뮬레이션 실행 중');
  });

  it('완료 후 결과를 열지 않았으면 결과 분석 대기', () => {
    expect(currentStageLabel(progress({ status: 'COMPLETED', totalPeople: 12 }))).toBe(
      '결과 분석 대기',
    );
  });

  it('결과 분석 화면을 열었으면 결과 분석 중', () => {
    expect(
      currentStageLabel(progress({ status: 'COMPLETED', totalPeople: 12, analysisOpened: true })),
    ).toBe('결과 분석 중');
  });

  it('실패는 실행 실패', () => {
    expect(currentStageLabel(progress({ status: 'FAILED', totalPeople: 12 }))).toBe('실행 실패');
  });

  it('취소는 실행 취소', () => {
    expect(currentStageLabel(progress({ status: 'CANCELLED', totalPeople: 12 }))).toBe('실행 취소');
  });
});
