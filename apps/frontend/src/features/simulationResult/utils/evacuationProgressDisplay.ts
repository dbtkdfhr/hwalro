export function getEvacuationProgressDisplay(
  evacuatedCount: number,
  evacuationRate: number,
  isPlaybackDataStale: boolean,
) {
  if (isPlaybackDataStale) {
    return {
      countLabel: '불러오는 중',
      rateLabel: '—',
    };
  }
  return {
    countLabel: `${evacuatedCount.toLocaleString('ko-KR')}명`,
    rateLabel: `${evacuationRate}%`,
  };
}
