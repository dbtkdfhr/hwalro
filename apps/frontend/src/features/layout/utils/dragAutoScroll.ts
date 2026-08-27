/** 가장자리에서 자동 스크롤이 시작되는 폭(px). 행 높이보다 약간 크게 잡아 손이 닿기 쉽게 한다. */
export const AUTO_SCROLL_EDGE_PX = 36;
/** 한 프레임에 움직이는 최대 거리(px). */
export const AUTO_SCROLL_MAX_STEP_PX = 18;

/**
 * 드래그 중인 포인터가 스크롤 영역 가장자리에 있을 때 움직일 거리를 구한다.
 * 위쪽이면 음수, 아래쪽이면 양수, 가운데면 0이다. 가장자리에 가까울수록 빨라진다.
 *
 * 네이티브 HTML5 드래그 앤 드롭은 스크롤 컨테이너를 대신 굴려주지 않기 때문에 직접 계산한다.
 */
export function autoScrollStep(
  clientY: number,
  rect: Pick<DOMRect, 'top' | 'bottom'>,
  edge: number = AUTO_SCROLL_EDGE_PX,
): number {
  const fromTop = clientY - rect.top;
  const fromBottom = rect.bottom - clientY;
  if (fromTop < edge) {
    const strength = Math.min(1, Math.max(0, (edge - fromTop) / edge));
    return -Math.ceil(strength * AUTO_SCROLL_MAX_STEP_PX);
  }
  if (fromBottom < edge) {
    const strength = Math.min(1, Math.max(0, (edge - fromBottom) / edge));
    return Math.ceil(strength * AUTO_SCROLL_MAX_STEP_PX);
  }
  return 0;
}
