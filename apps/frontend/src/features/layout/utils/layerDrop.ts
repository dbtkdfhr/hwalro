export type DropPosition = 'before' | 'after';

export function dropPositionAt(
  clientY: number,
  rect: Pick<DOMRect, 'top' | 'height'>,
): DropPosition {
  return clientY < rect.top + rect.height / 2 ? 'before' : 'after';
}

export function reorderRelative<T>(
  items: T[],
  draggedId: string,
  targetId: string,
  position: DropPosition,
  getId: (item: T) => string,
): T[] {
  const from = items.findIndex((item) => getId(item) === draggedId);
  const target = items.findIndex((item) => getId(item) === targetId);
  if (from < 0 || target < 0 || from === target) {
    return items;
  }

  const reordered = items.slice();
  const [moved] = reordered.splice(from, 1);
  const targetAfterRemoval = reordered.findIndex((item) => getId(item) === targetId);
  const insertAt = targetAfterRemoval + (position === 'after' ? 1 : 0);
  reordered.splice(insertAt, 0, moved);

  return reordered.every((item, index) => getId(item) === getId(items[index])) ? items : reordered;
}
