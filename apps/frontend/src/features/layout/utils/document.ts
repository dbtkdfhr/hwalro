import type { DrawingDocument, PointSelection, Vec2 } from '../types';

export function uid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `id-${Date.now()}-${Math.floor(Math.random() * 1e9)}`;
}

export function createEmptyDocument(): DrawingDocument {
  return {
    name: '더현대 지하 2층',
    width: 1000,
    height: 500,
    walls: [],
    outsideWalls: [],
    exits: [],
    pillars: [],
    fabrics: [],
    layoutTexts: [],
    background: null,
  };
}

export function emptySelection(): PointSelection {
  return {
    wallIds: [],
    outsideWallIds: [],
    exitIds: [],
    textIds: [],
    pillarIds: [],
    fabricIds: [],
  };
}

export function toggleId(list: string[], id: string): string[] {
  return list.includes(id) ? list.filter((x) => x !== id) : [...list, id];
}

export function nextWallName(doc: DrawingDocument): string {
  let max = 0;
  for (const wall of doc.walls) {
    const match = /^벽 (\d+)$/.exec(wall.name);
    if (match) {
      max = Math.max(max, Number(match[1]));
    }
  }
  return `벽 ${max + 1}`;
}

export function nextOutsideWallName(doc: DrawingDocument): string {
  let max = 0;
  for (const wall of doc.outsideWalls) {
    const match = /^외각벽 (\d+)$/.exec(wall.name);
    if (match) {
      max = Math.max(max, Number(match[1]));
    }
  }
  return `외각벽 ${max + 1}`;
}

export function nextExitName(doc: DrawingDocument): string {
  let max = 0;
  for (const exit of doc.exits) {
    const match = /^비상구 (\d+)$/.exec(exit.name);
    if (match) {
      max = Math.max(max, Number(match[1]));
    }
  }
  return `비상구 ${max + 1}`;
}

export function nextPillarName(doc: DrawingDocument): string {
  let max = 0;
  for (const pillar of doc.pillars) {
    const match = /^기둥 (\d+)$/.exec(pillar.name);
    if (match) {
      max = Math.max(max, Number(match[1]));
    }
  }
  return `기둥 ${max + 1}`;
}

export function nextFabricName(doc: DrawingDocument): string {
  let max = 0;
  for (const fabric of doc.fabrics) {
    const match = /^구조물 (\d+)$/.exec(fabric.name);
    if (match) {
      max = Math.max(max, Number(match[1]));
    }
  }
  return `구조물 ${max + 1}`;
}

interface AxisExtent {
  min: number;
  max: number;
}

function clampDelta(bound: number, extents: AxisExtent[], delta: number): number {
  let low = -Infinity;
  let high = Infinity;
  for (const extent of extents) {
    low = Math.max(low, -extent.min);
    high = Math.min(high, bound - extent.max);
  }
  if (low > high) {
    return delta;
  }
  return Math.max(low, Math.min(high, delta));
}

export function translateDoc(
  doc: DrawingDocument,
  selection: PointSelection,
  delta: Vec2,
): DrawingDocument {
  if (delta.x === 0 && delta.y === 0) {
    return doc;
  }
  const xExtents: AxisExtent[] = [];
  const yExtents: AxisExtent[] = [];
  const collect = (x1: number, y1: number, x2: number, y2: number) => {
    xExtents.push({ min: Math.min(x1, x2), max: Math.max(x1, x2) });
    yExtents.push({ min: Math.min(y1, y2), max: Math.max(y1, y2) });
  };
  for (const wall of doc.walls) {
    if (selection.wallIds.includes(wall.id)) {
      collect(wall.startX, wall.startY, wall.endX, wall.endY);
    }
  }
  for (const wall of doc.outsideWalls) {
    if (selection.outsideWallIds.includes(wall.id)) {
      collect(wall.startX, wall.startY, wall.endX, wall.endY);
    }
  }
  for (const exit of doc.exits) {
    if (selection.exitIds.includes(exit.id)) {
      collect(exit.startX, exit.startY, exit.endX, exit.endY);
    }
  }
  for (const pillar of doc.pillars) {
    if (selection.pillarIds.includes(pillar.id)) {
      collect(pillar.startX, pillar.startY, pillar.endX, pillar.endY);
    }
  }
  for (const fabric of doc.fabrics) {
    if (selection.fabricIds.includes(fabric.id)) {
      collect(fabric.startX, fabric.startY, fabric.endX, fabric.endY);
    }
  }
  for (const text of doc.layoutTexts) {
    if (selection.textIds.includes(text.id)) {
      collect(text.x, text.y, text.x, text.y);
    }
  }
  const dx = clampDelta(doc.width, xExtents, delta.x);
  const dy = clampDelta(doc.height, yExtents, delta.y);
  if (dx === 0 && dy === 0) {
    return doc;
  }
  const walls = doc.walls.map((wall) =>
    selection.wallIds.includes(wall.id)
      ? {
          ...wall,
          startX: wall.startX + dx,
          startY: wall.startY + dy,
          endX: wall.endX + dx,
          endY: wall.endY + dy,
        }
      : wall,
  );
  const outsideWalls = doc.outsideWalls.map((wall) =>
    selection.outsideWallIds.includes(wall.id)
      ? {
          ...wall,
          startX: wall.startX + dx,
          startY: wall.startY + dy,
          endX: wall.endX + dx,
          endY: wall.endY + dy,
        }
      : wall,
  );
  const exits = doc.exits.map((exit) =>
    selection.exitIds.includes(exit.id)
      ? {
          ...exit,
          startX: exit.startX + dx,
          startY: exit.startY + dy,
          endX: exit.endX + dx,
          endY: exit.endY + dy,
        }
      : exit,
  );
  const pillars = doc.pillars.map((pillar) =>
    selection.pillarIds.includes(pillar.id)
      ? {
          ...pillar,
          startX: pillar.startX + dx,
          startY: pillar.startY + dy,
          endX: pillar.endX + dx,
          endY: pillar.endY + dy,
        }
      : pillar,
  );
  const fabrics = doc.fabrics.map((fabric) =>
    selection.fabricIds.includes(fabric.id)
      ? {
          ...fabric,
          startX: fabric.startX + dx,
          startY: fabric.startY + dy,
          endX: fabric.endX + dx,
          endY: fabric.endY + dy,
        }
      : fabric,
  );
  const layoutTexts = doc.layoutTexts.map((text) =>
    selection.textIds.includes(text.id) ? { ...text, x: text.x + dx, y: text.y + dy } : text,
  );
  return { ...doc, walls, outsideWalls, exits, pillars, fabrics, layoutTexts };
}

export function isInsideBounds(
  doc: Pick<DrawingDocument, 'width' | 'height'>,
  x: number,
  y: number,
): boolean {
  return x >= 0 && x <= doc.width && y >= 0 && y <= doc.height;
}

export function isRectInsideBounds(
  doc: Pick<DrawingDocument, 'width' | 'height'>,
  x1: number,
  y1: number,
  x2: number,
  y2: number,
): boolean {
  const minX = Math.min(x1, x2);
  const maxX = Math.max(x1, x2);
  const minY = Math.min(y1, y2);
  const maxY = Math.max(y1, y2);
  return isInsideBounds(doc, minX, minY) && isInsideBounds(doc, maxX, maxY);
}
