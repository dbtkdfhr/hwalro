import { describe, expect, it } from 'vitest';
import type { ZoneRect } from '../api/layoutMetadataApi';
import {
  createZoneRectHistory,
  pushZoneRectCommand,
  redoZoneRectCommand,
  undoZoneRectCommand,
} from './zoneRectHistory';

const before: ZoneRect = { x: 1, y: 2, width: 3, height: 4 };
const after: ZoneRect = { x: 5, y: 6, width: 7, height: 8 };

describe('zone rectangle history', () => {
  it('pushes, undoes, and redoes a rectangle command', () => {
    const committed = pushZoneRectCommand(createZoneRectHistory(), {
      zoneId: 10,
      before,
      after,
    });
    const undone = undoZoneRectCommand(committed);
    const redone = undone === null ? null : redoZoneRectCommand(undone.history);

    expect(undone?.command.before).toEqual(before);
    expect(undone?.history.past).toEqual([]);
    expect(redone?.command.after).toEqual(after);
    expect(redone?.history.past).toHaveLength(1);
  });

  it('clears redo commands after a new commit', () => {
    const first = pushZoneRectCommand(createZoneRectHistory(), {
      zoneId: 10,
      before,
      after,
    });
    const undone = undoZoneRectCommand(first);
    expect(undone).not.toBeNull();
    const next = pushZoneRectCommand(undone!.history, {
      zoneId: 11,
      before,
      after: { ...after, x: 9 },
    });
    expect(next.future).toEqual([]);
  });
});
