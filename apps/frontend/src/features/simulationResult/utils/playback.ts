export interface FramePair<T> {
  previous: T;
  next: T;
  ratio: number;
}

export function selectFramePair<T extends { timeSeconds: number }>(
  frames: readonly T[],
  timeSeconds: number,
): FramePair<T> {
  if (frames.length === 0) throw new Error('재생 프레임이 없습니다.');
  if (timeSeconds <= frames[0].timeSeconds) {
    return { previous: frames[0], next: frames[0], ratio: 0 };
  }
  const last = frames[frames.length - 1];
  if (timeSeconds >= last.timeSeconds) return { previous: last, next: last, ratio: 0 };
  let low = 0;
  let high = frames.length - 1;
  while (low + 1 < high) {
    const middle = Math.floor((low + high) / 2);
    if (frames[middle].timeSeconds <= timeSeconds) low = middle;
    else high = middle;
  }
  const previous = frames[low];
  const next = frames[high];
  return {
    previous,
    next,
    ratio: (timeSeconds - previous.timeSeconds) / (next.timeSeconds - previous.timeSeconds),
  };
}

export function interpolatePositions(
  previous: Float32Array,
  next: Float32Array,
  ratio: number,
  destination: Float32Array,
) {
  const length = Math.min(previous.length, next.length, destination.length);
  for (let index = 0; index < length; index += 2) {
    const previousInactive = previous[index] < -1000 || previous[index + 1] < -1000;
    const nextInactive = next[index] < -1000 || next[index + 1] < -1000;
    if (previousInactive) {
      destination[index] = -10_000;
      destination[index + 1] = -10_000;
    } else if (nextInactive) {
      destination[index] = previous[index];
      destination[index + 1] = previous[index + 1];
    } else {
      destination[index] = previous[index] + (next[index] - previous[index]) * ratio;
      destination[index + 1] =
        previous[index + 1] + (next[index + 1] - previous[index + 1]) * ratio;
    }
  }
}

export function formatDuration(seconds: number) {
  const safeSeconds = Math.max(0, Math.round(seconds));
  const minutes = Math.floor(safeSeconds / 60);
  return `${String(minutes).padStart(2, '0')}:${String(safeSeconds % 60).padStart(2, '0')}`;
}
