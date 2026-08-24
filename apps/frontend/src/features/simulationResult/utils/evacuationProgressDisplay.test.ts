import { describe, expect, it } from 'vitest';
import { getEvacuationProgressDisplay } from './evacuationProgressDisplay';

describe('getEvacuationProgressDisplay', () => {
  it('hides time-sensitive metrics while playback data is stale', () => {
    expect(getEvacuationProgressDisplay(980, 98, true)).toEqual({
      countLabel: '불러오는 중',
      rateLabel: '-',
    });
  });

  it('formats loaded evacuation metrics', () => {
    expect(getEvacuationProgressDisplay(1_234, 62, false)).toEqual({
      countLabel: '1,234명',
      rateLabel: '62%',
    });
  });
});
