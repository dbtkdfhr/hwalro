import type { EvacuationStatus, EvacuationUnavailableReason, ExitChoice } from '../api/zoneApi';

export interface StatusPresentation {
  /** 화면에 그대로 쓰는 안내 문구. */
  message: string;
  tone: 'ok' | 'warning' | 'muted';
  /** 경로선을 그릴 수 있는 상태인가. */
  hasRoute: boolean;
}

export function evacuationStatusPresentation(
  status: EvacuationStatus,
  exitChoice: ExitChoice | null = null,
  unavailableReason: EvacuationUnavailableReason | null = null,
): StatusPresentation {
  switch (status) {
    case 'AVAILABLE':
      return {
        message:
          exitChoice === 'NEAREST'
            ? '이 구역에는 담당 비상구가 지정되어 있지 않아, 가장 가까운 비상구로 안내합니다.'
            : '구역에 지정된 비상구로 안내합니다.',
        tone: 'ok',
        hasRoute: true,
      };
    case 'NOT_CONFIGURED':
      return {
        message:
          unavailableReason === 'ASSIGNED_EXIT_NOT_FOUND'
            ? '담당 비상구가 현재 도면에 없습니다. 안전 담당자에게 비상구 지정을 요청하세요.'
            : '이 도면에는 비상구가 없어 대피 경로를 안내할 수 없습니다. 안전 담당자에게 문의하세요.',
        tone: 'muted',
        hasRoute: false,
      };
    case 'UNREACHABLE':
      return {
        message:
          unavailableReason === 'NO_WALKABLE_ORIGIN_IN_ZONE'
            ? '담당 구역 안에서 사람이 설 수 있는 시작점을 찾지 못했습니다. 안전 담당자에게 즉시 알리세요.'
            : '비상구까지 사람이 지나갈 수 있는 경로를 찾지 못했습니다. 통로가 막혀 있을 수 있으니 안전 담당자에게 즉시 알리세요.',
        tone: 'warning',
        hasRoute: false,
      };
  }
}

/** 경로에서 가장 좁은 지점이 얼마나 위험한지. 반폭 기준이라 실제 통로 폭은 이 값의 두 배다. */
export function narrowPassageWarning(narrowestMeters: number | null): string | null {
  if (narrowestMeters === null || narrowestMeters <= 0) {
    return null;
  }
  if (narrowestMeters < 0.5) {
    return '경로에 한 사람이 겨우 지나는 좁은 구간이 있습니다. 대피 시 정체가 생기기 쉽습니다.';
  }
  if (narrowestMeters < 0.9) {
    return '경로에 두 사람이 엇갈리기 어려운 구간이 있습니다.';
  }
  return null;
}
