import { useCallback, useEffect, useRef, useState } from 'react';
import type { ZoneRect } from '../api/layoutMetadataApi';
import {
  createZoneRectHistory,
  pushZoneRectCommand,
  redoZoneRectCommand,
  undoZoneRectCommand,
  type ZoneRectCommand,
  type ZoneRectHistory,
} from '../state/zoneRectHistory';

type PersistZoneRect = (zoneId: number, rect: ZoneRect) => Promise<boolean>;

function sameRect(left: ZoneRect, right: ZoneRect): boolean {
  return (
    left.x === right.x &&
    left.y === right.y &&
    left.width === right.width &&
    left.height === right.height
  );
}

export function useZoneRectHistory(resetKey: string, persist: PersistZoneRect) {
  const [history, setHistory] = useState<ZoneRectHistory>(createZoneRectHistory);
  const [pending, setPending] = useState(false);
  const pendingRef = useRef(false);

  useEffect(() => {
    setHistory(createZoneRectHistory());
    pendingRef.current = false;
    setPending(false);
  }, [resetKey]);

  const commit = useCallback(
    async (command: ZoneRectCommand) => {
      if (pendingRef.current || sameRect(command.before, command.after)) return false;
      pendingRef.current = true;
      setPending(true);
      const saved = await persist(command.zoneId, command.after);
      if (saved) setHistory((current) => pushZoneRectCommand(current, command));
      pendingRef.current = false;
      setPending(false);
      return saved;
    },
    [persist],
  );

  const undo = useCallback(async () => {
    if (pendingRef.current) return false;
    const step = undoZoneRectCommand(history);
    if (!step) return false;
    pendingRef.current = true;
    setPending(true);
    const saved = await persist(step.command.zoneId, step.command.before);
    if (saved) setHistory(step.history);
    pendingRef.current = false;
    setPending(false);
    return saved;
  }, [history, persist]);

  const redo = useCallback(async () => {
    if (pendingRef.current) return false;
    const step = redoZoneRectCommand(history);
    if (!step) return false;
    pendingRef.current = true;
    setPending(true);
    const saved = await persist(step.command.zoneId, step.command.after);
    if (saved) setHistory(step.history);
    pendingRef.current = false;
    setPending(false);
    return saved;
  }, [history, persist]);

  return {
    canUndo: history.past.length > 0,
    canRedo: history.future.length > 0,
    pending,
    commit,
    undo,
    redo,
  };
}
