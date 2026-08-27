import type { ZoneRect } from '../api/layoutMetadataApi';

export interface ZoneRectCommand {
  zoneId: number;
  before: ZoneRect;
  after: ZoneRect;
}

export interface ZoneRectHistory {
  past: ZoneRectCommand[];
  future: ZoneRectCommand[];
}

interface HistoryStep {
  command: ZoneRectCommand;
  history: ZoneRectHistory;
}

export function createZoneRectHistory(): ZoneRectHistory {
  return { past: [], future: [] };
}

export function pushZoneRectCommand(
  history: ZoneRectHistory,
  command: ZoneRectCommand,
): ZoneRectHistory {
  return { past: [...history.past, command], future: [] };
}

export function undoZoneRectCommand(history: ZoneRectHistory): HistoryStep | null {
  const command = history.past[history.past.length - 1];
  if (!command) {
    return null;
  }
  return {
    command,
    history: {
      past: history.past.slice(0, -1),
      future: [command, ...history.future],
    },
  };
}

export function redoZoneRectCommand(history: ZoneRectHistory): HistoryStep | null {
  const [command, ...future] = history.future;
  if (!command) {
    return null;
  }
  return {
    command,
    history: {
      past: [...history.past, command],
      future,
    },
  };
}
