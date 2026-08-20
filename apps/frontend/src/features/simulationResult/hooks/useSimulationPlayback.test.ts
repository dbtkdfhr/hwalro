import { describe, expect, it } from 'vitest';
import { resolveScrubEnd } from './useSimulationPlayback';

describe('resolveScrubEnd', () => {
  it('marks playback complete when an active playback scrub reaches the end', () => {
    expect(resolveScrubEnd(true, 120, 120)).toEqual({
      completedPlayback: true,
      shouldResume: false,
    });
  });

  it('does not treat a paused manual seek to the end as completed playback', () => {
    expect(resolveScrubEnd(false, 120, 120)).toEqual({
      completedPlayback: false,
      shouldResume: false,
    });
  });

  it('resumes playback when an active playback scrub ends before the duration', () => {
    expect(resolveScrubEnd(true, 80, 120)).toEqual({
      completedPlayback: false,
      shouldResume: true,
    });
  });
});
