import { describe, expect, it } from 'vitest';
import { timelineChunkWindow } from './timeline';

describe('timelineChunkWindow', () => {
  it('keeps only adjacent timeline chunks', () => {
    expect(timelineChunkWindow(0, 5)).toEqual([0, 1]);
    expect(timelineChunkWindow(2, 5)).toEqual([1, 2, 3]);
    expect(timelineChunkWindow(4, 5)).toEqual([3, 4]);
  });
});
