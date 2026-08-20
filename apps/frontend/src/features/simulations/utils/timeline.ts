export function timelineChunkWindow(current: number, count: number): number[] {
  return [current - 1, current, current + 1].filter(
    (sequence) => sequence >= 0 && sequence < count,
  );
}
