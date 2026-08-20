import { useCallback, useEffect, useRef, useState } from 'react';

const SCRUB_CHUNK_LOOKUP_DELAY_MS = 100;

export function resolveScrubEnd(wasPlaying: boolean, timeSeconds: number, durationSeconds: number) {
  const completedPlayback = wasPlaying && timeSeconds >= durationSeconds;
  return {
    completedPlayback,
    shouldResume: wasPlaying && !completedPlayback,
  };
}

export function useSimulationPlayback(durationSeconds: number) {
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTimeSeconds, setCurrentTimeSeconds] = useState(0);
  const [scrubTimeSeconds, setScrubTimeSeconds] = useState<number | null>(null);
  const [debouncedScrubTimeSeconds, setDebouncedScrubTimeSeconds] = useState<number | null>(null);
  const [playbackRate, setPlaybackRateState] = useState(1);
  const [hasCompletedPlayback, setHasCompletedPlayback] = useState(false);
  const timeRef = useRef(0);
  const previousTimestampRef = useRef<number | null>(null);
  const resumeAfterScrubRef = useRef(false);

  const clampTime = useCallback(
    (value: number) => Math.min(durationSeconds, Math.max(0, value)),
    [durationSeconds],
  );

  const displayTimeSeconds = scrubTimeSeconds ?? currentTimeSeconds;
  const chunkLookupTimeSeconds =
    scrubTimeSeconds === null
      ? currentTimeSeconds
      : (debouncedScrubTimeSeconds ?? currentTimeSeconds);

  useEffect(() => {
    timeRef.current = currentTimeSeconds;
  }, [currentTimeSeconds]);

  useEffect(() => {
    if (scrubTimeSeconds === null) {
      setDebouncedScrubTimeSeconds(null);
      return;
    }
    const timeout = window.setTimeout(
      () => setDebouncedScrubTimeSeconds(scrubTimeSeconds),
      SCRUB_CHUNK_LOOKUP_DELAY_MS,
    );
    return () => window.clearTimeout(timeout);
  }, [scrubTimeSeconds]);

  useEffect(() => {
    if (!isPlaying) {
      previousTimestampRef.current = null;
      return;
    }
    let animationFrame = 0;
    let lastUiUpdate = 0;
    const tick = (timestamp: number) => {
      const previous = previousTimestampRef.current ?? timestamp;
      previousTimestampRef.current = timestamp;
      timeRef.current = Math.min(
        durationSeconds,
        timeRef.current + ((timestamp - previous) / 1000) * playbackRate,
      );
      if (timestamp - lastUiUpdate > 33 || timeRef.current >= durationSeconds) {
        setCurrentTimeSeconds(timeRef.current);
        lastUiUpdate = timestamp;
      }
      if (timeRef.current >= durationSeconds) {
        setHasCompletedPlayback(true);
        setIsPlaying(false);
        return;
      }
      animationFrame = window.requestAnimationFrame(tick);
    };
    animationFrame = window.requestAnimationFrame(tick);
    return () => window.cancelAnimationFrame(animationFrame);
  }, [durationSeconds, isPlaying, playbackRate]);

  const seek = useCallback(
    (value: number) => {
      const next = clampTime(value);
      timeRef.current = next;
      setCurrentTimeSeconds(next);
      previousTimestampRef.current = null;
    },
    [clampTime],
  );

  const play = useCallback(() => {
    if (timeRef.current >= durationSeconds) seek(0);
    setIsPlaying(true);
  }, [durationSeconds, seek]);

  const startScrub = useCallback(
    (value: number) => {
      resumeAfterScrubRef.current = isPlaying;
      setIsPlaying(false);
      setScrubTimeSeconds(clampTime(value));
    },
    [clampTime, isPlaying],
  );

  const scrubTo = useCallback(
    (value: number) => setScrubTimeSeconds(clampTime(value)),
    [clampTime],
  );

  const endScrub = useCallback(
    (value: number) => {
      const next = clampTime(value);
      const { completedPlayback, shouldResume } = resolveScrubEnd(
        resumeAfterScrubRef.current,
        next,
        durationSeconds,
      );
      resumeAfterScrubRef.current = false;
      seek(next);
      setScrubTimeSeconds(null);
      if (completedPlayback) setHasCompletedPlayback(true);
      if (shouldResume) setIsPlaying(true);
    },
    [clampTime, durationSeconds, seek],
  );

  return {
    isPlaying,
    currentTimeSeconds,
    displayTimeSeconds,
    chunkLookupTimeSeconds,
    isScrubbing: scrubTimeSeconds !== null,
    playbackRate,
    hasCompletedPlayback,
    play,
    pause: () => setIsPlaying(false),
    toggle: () => (isPlaying ? setIsPlaying(false) : play()),
    seek,
    startScrub,
    scrubTo,
    endScrub,
    setPlaybackRate: (rate: number) => setPlaybackRateState(rate),
  };
}
