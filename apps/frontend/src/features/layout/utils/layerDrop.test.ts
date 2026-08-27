import { describe, expect, it } from 'vitest';
import { dropPositionAt, reorderRelative } from './layerDrop';

const ids = (items: Array<{ id: string }>) => items.map((item) => item.id);
const fixture = () => ['a', 'b', 'c', 'd'].map((id) => ({ id }));

describe('layer drop ordering', () => {
  it('moves in either direction before or after the target', () => {
    expect(ids(reorderRelative(fixture(), 'a', 'c', 'after', (item) => item.id))).toEqual([
      'b',
      'c',
      'a',
      'd',
    ]);
    expect(ids(reorderRelative(fixture(), 'd', 'b', 'before', (item) => item.id))).toEqual([
      'a',
      'd',
      'b',
      'c',
    ]);
  });

  it('returns the original list for equivalent and invalid drops', () => {
    const items = fixture();
    expect(reorderRelative(items, 'a', 'b', 'before', (item) => item.id)).toBe(items);
    expect(reorderRelative(items, 'b', 'a', 'after', (item) => item.id)).toBe(items);
    expect(reorderRelative(items, 'a', 'a', 'after', (item) => item.id)).toBe(items);
    expect(reorderRelative(items, 'missing', 'a', 'before', (item) => item.id)).toBe(items);
  });

  it('uses the row midpoint for the insertion preview', () => {
    expect(dropPositionAt(19, { top: 10, height: 20 })).toBe('before');
    expect(dropPositionAt(20, { top: 10, height: 20 })).toBe('after');
  });
});
