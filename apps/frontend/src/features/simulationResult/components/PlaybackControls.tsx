import { useRef } from 'react';
import { Pause, Play } from 'lucide-react';
import { formatDuration } from '../utils/playback';

interface Props {
  currentTimeSeconds: number;
  durationSeconds: number;
  isPlaying: boolean;
  playbackRate: number;
  resultsVisible: boolean;
  isBuffering: boolean;
  onToggle: () => void;
  onSeek: (timeSeconds: number) => void;
  onScrubStart: (timeSeconds: number) => void;
  onScrubChange: (timeSeconds: number) => void;
  onScrubEnd: (timeSeconds: number) => void;
  onPlaybackRateChange: (rate: number) => void;
  onRevealResults: () => void;
}

export function PlaybackControls({
  currentTimeSeconds,
  durationSeconds,
  isPlaying,
  playbackRate,
  resultsVisible,
  isBuffering,
  onToggle,
  onSeek,
  onScrubStart,
  onScrubChange,
  onScrubEnd,
  onPlaybackRateChange,
  onRevealResults,
}: Props) {
  const nextPlaybackRate = playbackRate === 1 ? 2 : playbackRate === 2 ? 4 : 1;
  const scrubTimeRef = useRef<number | null>(null);

  const beginScrub = (element: HTMLInputElement, pointerId: number) => {
    const timeSeconds = Number(element.value);
    scrubTimeRef.current = timeSeconds;
    onScrubStart(timeSeconds);
    element.setPointerCapture(pointerId);
  };

  const updateTime = (timeSeconds: number) => {
    if (scrubTimeRef.current === null) {
      onSeek(timeSeconds);
      return;
    }
    scrubTimeRef.current = timeSeconds;
    onScrubChange(timeSeconds);
  };

  const commitScrub = () => {
    const timeSeconds = scrubTimeRef.current;
    if (timeSeconds === null) return;
    scrubTimeRef.current = null;
    onScrubEnd(timeSeconds);
  };

  return (
    <div className="playback-controls">
      <button
        type="button"
        className="playback-toggle"
        aria-label={isPlaying ? '일시정지' : '재생'}
        title={isPlaying ? '일시정지' : '재생'}
        onClick={onToggle}
      >
        {isPlaying ? <Pause aria-hidden="true" /> : <Play aria-hidden="true" />}
      </button>
      <strong>{formatDuration(currentTimeSeconds)}</strong>
      <input
        aria-label="재생 위치"
        aria-valuetext={formatDuration(currentTimeSeconds)}
        type="range"
        min="0"
        max={durationSeconds}
        step="0.1"
        value={currentTimeSeconds}
        onPointerDown={(event) => beginScrub(event.currentTarget, event.pointerId)}
        onPointerUp={commitScrub}
        onPointerCancel={commitScrub}
        onBlur={commitScrub}
        onChange={(event) => updateTime(Number(event.target.value))}
      />
      {isBuffering && (
        <span className="playback-buffering" role="status" aria-live="polite">
          재생 데이터 불러오는 중
        </span>
      )}
      <span>{formatDuration(durationSeconds)}</span>
      <button
        type="button"
        className="playback-rate"
        aria-label={`현재 ${playbackRate}배속, 다음 재생 속도로 변경`}
        onClick={() => onPlaybackRateChange(nextPlaybackRate)}
      >
        {playbackRate}×
      </button>
      <button
        type="button"
        className={`playback-result ${resultsVisible ? 'is-visible' : ''}`}
        aria-pressed={resultsVisible}
        disabled={resultsVisible}
        onClick={onRevealResults}
      >
        {resultsVisible ? '결과 표시됨' : '결과 보기'}
      </button>
    </div>
  );
}
