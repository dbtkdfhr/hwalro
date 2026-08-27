import { describe, expect, it } from 'vitest';
import { evacuationStatusPresentation, narrowPassageWarning } from './evacuationStatus';

describe('evacuationStatusPresentation', () => {
  it('AVAILABLE만 경로를 그린다', () => {
    expect(evacuationStatusPresentation('AVAILABLE').hasRoute).toBe(true);
    expect(evacuationStatusPresentation('UNREACHABLE').hasRoute).toBe(false);
    expect(evacuationStatusPresentation('NOT_CONFIGURED').hasRoute).toBe(false);
  });

  it('비상구가 없는 것과 길이 막힌 것을 다른 문구로 구분한다', () => {
    expect(evacuationStatusPresentation('NOT_CONFIGURED').message).toContain('비상구가 없어');
    expect(evacuationStatusPresentation('UNREACHABLE').message).toContain('막혀');
  });

  it('길이 막힌 것은 경고 톤이다', () => {
    expect(evacuationStatusPresentation('UNREACHABLE').tone).toBe('warning');
    expect(evacuationStatusPresentation('AVAILABLE').tone).toBe('ok');
  });

  it('배정 비상구가 없어 대신 고른 경우임을 알린다', () => {
    // 안내를 그대로 따르되, 담당 비상구가 정해지지 않은 상태라는 사실은 알아야 한다.
    expect(evacuationStatusPresentation('AVAILABLE', 'NEAREST').message).toContain('가장 가까운');
    expect(evacuationStatusPresentation('AVAILABLE', 'ASSIGNED').message).toContain('지정된');
  });

  it('지정 출구 누락과 구역 내부 시작점 부재를 구체적으로 구분한다', () => {
    expect(
      evacuationStatusPresentation('NOT_CONFIGURED', 'ASSIGNED', 'ASSIGNED_EXIT_NOT_FOUND').message,
    ).toContain('현재 도면에 없습니다');
    expect(
      evacuationStatusPresentation('UNREACHABLE', 'NEAREST', 'NO_WALKABLE_ORIGIN_IN_ZONE').message,
    ).toContain('담당 구역 안');
  });
});

describe('narrowPassageWarning', () => {
  it('넉넉한 통로에는 경고하지 않는다', () => {
    expect(narrowPassageWarning(1.5)).toBeNull();
    expect(narrowPassageWarning(0.9)).toBeNull();
  });

  it('좁을수록 강한 문구로 알린다', () => {
    expect(narrowPassageWarning(0.7)).toContain('엇갈리기 어려운');
    expect(narrowPassageWarning(0.4)).toContain('정체');
  });

  it('경로가 없으면 경고할 것도 없다', () => {
    expect(narrowPassageWarning(0)).toBeNull();
    expect(narrowPassageWarning(null)).toBeNull();
  });
});
