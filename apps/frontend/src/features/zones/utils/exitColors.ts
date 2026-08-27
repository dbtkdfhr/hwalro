/**
 * 비상구별 색. 한 구역이 여러 비상구로 갈라질 때 어느 영역이 어디로 나가는지 색으로 구분한다.
 *
 * 도면에 비상구가 16개인 사례가 실제로 있어 그보다 넉넉하게 20색을 둔다. 인접한 색끼리 색상환에서
 * 멀도록 배열해, 옆에 붙은 영역이 비슷한 색으로 보이지 않게 했다.
 */
const EXIT_COLORS = [
  '#1f77b4',
  '#e6550d',
  '#2ca02c',
  '#d62728',
  '#9467bd',
  '#17becf',
  '#bcbd22',
  '#e377c2',
  '#8c564b',
  '#7f7f7f',
  '#3182bd',
  '#fd8d3c',
  '#31a354',
  '#e7298a',
  '#756bb1',
  '#66c2a5',
  '#b15928',
  '#fdbf6f',
  '#6a51a3',
  '#01665e',
] as const;

/**
 * 비상구 ID를 색으로 바꾼다.
 *
 * 도면 안에서 몇 번째 비상구인지로 색을 정한다. ID를 직접 나눗셈하면 도면을 저장할 때마다 ID가 새로
 * 매겨져 색이 통째로 바뀐다.
 */
export function exitColorOf(exitId: number, orderedExitIds: readonly number[]): string {
  const index = orderedExitIds.indexOf(exitId);
  return EXIT_COLORS[(index < 0 ? 0 : index) % EXIT_COLORS.length];
}

export const EXIT_COLOR_COUNT = EXIT_COLORS.length;
