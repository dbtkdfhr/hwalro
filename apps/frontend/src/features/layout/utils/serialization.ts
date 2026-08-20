import type {
  BackgroundImage,
  DrawingDocument,
  Exit,
  Fabric,
  OutsideWall,
  Pillar,
  SerializedDocument,
  Wall,
} from '../types';

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function toFiniteNumber(value: unknown, key: string): number {
  const num =
    typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : Number.NaN;
  if (!Number.isFinite(num)) {
    throw new Error(`잘못된 숫자 값: ${key}`);
  }
  return num;
}

function wallNameIndex(name: string): number | null {
  const match = /^벽 (\d+)$/.exec(name);
  return match ? Number(match[1]) : null;
}

function outsideWallNameIndex(name: string): number | null {
  const match = /^외각벽 (\d+)$/.exec(name);
  return match ? Number(match[1]) : null;
}

function exitNameIndex(name: string): number | null {
  const match = /^비상구 (\d+)$/.exec(name);
  return match ? Number(match[1]) : null;
}

interface SerializedRect {
  id: string;
  name: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
}

function elementNameIndex(pattern: RegExp, name: string): number | null {
  const match = pattern.exec(name);
  return match ? Number(match[1]) : null;
}

function parseRects(
  raw: unknown,
  key: string,
  namePattern: RegExp,
  label: string,
  idPrefix: string,
): SerializedRect[] {
  if (raw !== undefined && !Array.isArray(raw)) {
    throw new Error(`${key}는 배열이어야 합니다`);
  }
  const entries = (raw ?? []) as unknown[];
  const result: SerializedRect[] = [];
  let maxIndex = 0;
  const unnamed: Array<{ rect: SerializedRect; order: number }> = [];
  for (let i = 0; i < entries.length; i++) {
    const entry = entries[i];
    if (!isRecord(entry)) {
      throw new Error(`${key}[${i}]가 객체가 아닙니다`);
    }
    const parsedName = parseElementName(entry.name);
    if (parsedName) {
      const idx = elementNameIndex(namePattern, parsedName);
      if (idx !== null) {
        maxIndex = Math.max(maxIndex, idx);
      }
    }
    const rect: SerializedRect = {
      id: `${idPrefix}-${i}`,
      name: parsedName ?? '',
      startX: toFiniteNumber(entry.startX ?? entry.start_x, `${key}[${i}].startX`),
      startY: toFiniteNumber(entry.startY ?? entry.start_y, `${key}[${i}].startY`),
      endX: toFiniteNumber(entry.endX ?? entry.end_x, `${key}[${i}].endX`),
      endY: toFiniteNumber(entry.endY ?? entry.end_y, `${key}[${i}].endY`),
      rotation: toFiniteNumber(entry.rotation ?? 0, `${key}[${i}].rotation`),
    };
    if (parsedName) {
      result.push(rect);
    } else {
      unnamed.push({ rect, order: i });
    }
  }

  for (const { rect, order } of unnamed) {
    maxIndex += 1;
    result.splice(order, 0, { ...rect, name: `${label} ${maxIndex}` });
  }
  return result;
}

export function parseElementName(name: unknown): string | null {
  return typeof name === 'string' && name.trim() !== '' ? name.trim() : null;
}

export function toSerialized(doc: DrawingDocument): SerializedDocument {
  return {
    name: doc.name,
    width: doc.width,
    height: doc.height,
    walls: doc.walls.map((wall) => ({
      name: wall.name,
      startX: wall.startX,
      startY: wall.startY,
      endX: wall.endX,
      endY: wall.endY,
    })),
    outsideWalls: doc.outsideWalls.map((wall) => ({
      name: wall.name,
      startX: wall.startX,
      startY: wall.startY,
      endX: wall.endX,
      endY: wall.endY,
    })),
    exits: doc.exits.map((exit) => ({
      name: exit.name,
      startX: exit.startX,
      startY: exit.startY,
      endX: exit.endX,
      endY: exit.endY,
    })),
    pillars: doc.pillars.map((pillar) => ({
      name: pillar.name,
      startX: pillar.startX,
      startY: pillar.startY,
      endX: pillar.endX,
      endY: pillar.endY,
      rotation: pillar.rotation,
    })),
    fabrics: doc.fabrics.map((fabric) => ({
      name: fabric.name,
      startX: fabric.startX,
      startY: fabric.startY,
      endX: fabric.endX,
      endY: fabric.endY,
      rotation: fabric.rotation,
    })),
    layoutTexts: doc.layoutTexts.map((text) => ({ text: text.text, x: text.x, y: text.y })),
    background: doc.background
      ? {
          image: doc.background.image,
          x: doc.background.x,
          y: doc.background.y,
          width: doc.background.width,
          height: doc.background.height,
          opacity: doc.background.opacity,
          aspect: doc.background.aspect,
        }
      : null,
  };
}

export function toJson(doc: DrawingDocument): string {
  return JSON.stringify(toSerialized(doc), null, 2);
}

export function parseWallName(name: unknown): string | null {
  return parseElementName(name);
}

export function fromSerialized(data: unknown): DrawingDocument {
  if (!isRecord(data)) {
    throw new Error('최상위 구조가 객체가 아닙니다');
  }
  const name = typeof data.name === 'string' && data.name.trim() !== '' ? data.name.trim() : '도면';
  const width = toFiniteNumber(data.width ?? 1000, 'width');
  const height = toFiniteNumber(data.height ?? 500, 'height');
  if (width <= 0 || height <= 0) {
    throw new Error('width/height는 0보다 커야 합니다');
  }

  if (data.walls !== undefined && !Array.isArray(data.walls)) {
    throw new Error('walls는 배열이어야 합니다');
  }
  if (data.outsideWalls !== undefined && !Array.isArray(data.outsideWalls)) {
    throw new Error('outsideWalls는 배열이어야 합니다');
  }
  if (data.exits !== undefined && !Array.isArray(data.exits)) {
    throw new Error('exits는 배열이어야 합니다');
  }
  if (data.layoutTexts !== undefined && !Array.isArray(data.layoutTexts)) {
    throw new Error('layoutTexts는 배열이어야 합니다');
  }

  const walls: Wall[] = [];
  const rawWalls = (data.walls ?? []) as unknown[];
  let maxIndex = 0;
  const unnamed: Array<{ wall: Wall; order: number }> = [];
  for (let i = 0; i < rawWalls.length; i++) {
    const entry = rawWalls[i];
    if (!isRecord(entry)) {
      throw new Error(`walls[${i}]가 객체가 아닙니다`);
    }
    const parsedName = parseWallName(entry.name);
    if (parsedName) {
      const idx = wallNameIndex(parsedName);
      if (idx !== null) {
        maxIndex = Math.max(maxIndex, idx);
      }
    }
    const wall: Wall = {
      id: `loaded-wall-${i}`,
      name: parsedName ?? '',
      startX: toFiniteNumber(entry.startX ?? entry.start_x, `walls[${i}].startX`),
      startY: toFiniteNumber(entry.startY ?? entry.start_y, `walls[${i}].startY`),
      endX: toFiniteNumber(entry.endX ?? entry.end_x, `walls[${i}].endX`),
      endY: toFiniteNumber(entry.endY ?? entry.end_y, `walls[${i}].endY`),
    };
    if (parsedName) {
      walls.push(wall);
    } else {
      unnamed.push({ wall, order: i });
    }
  }

  for (const { wall, order } of unnamed) {
    maxIndex += 1;
    walls.splice(order, 0, { ...wall, name: `벽 ${maxIndex}` });
  }

  const rawOutsideWalls = (data.outsideWalls ?? []) as unknown[];
  const outsideWalls: OutsideWall[] = [];
  let maxOutsideWallIndex = 0;
  const unnamedOutsideWalls: Array<{ wall: OutsideWall; order: number }> = [];
  for (let i = 0; i < rawOutsideWalls.length; i++) {
    const entry = rawOutsideWalls[i];
    if (!isRecord(entry)) {
      throw new Error(`outsideWalls[${i}]가 객체가 아닙니다`);
    }
    const parsedName = parseWallName(entry.name);
    if (parsedName) {
      const idx = outsideWallNameIndex(parsedName);
      if (idx !== null) {
        maxOutsideWallIndex = Math.max(maxOutsideWallIndex, idx);
      }
    }
    const wall: OutsideWall = {
      id: `loaded-outside-wall-${i}`,
      name: parsedName ?? '',
      startX: toFiniteNumber(entry.startX ?? entry.start_x, `outsideWalls[${i}].startX`),
      startY: toFiniteNumber(entry.startY ?? entry.start_y, `outsideWalls[${i}].startY`),
      endX: toFiniteNumber(entry.endX ?? entry.end_x, `outsideWalls[${i}].endX`),
      endY: toFiniteNumber(entry.endY ?? entry.end_y, `outsideWalls[${i}].endY`),
    };
    if (parsedName) {
      outsideWalls.push(wall);
    } else {
      unnamedOutsideWalls.push({ wall, order: i });
    }
  }

  for (const { wall, order } of unnamedOutsideWalls) {
    maxOutsideWallIndex += 1;
    outsideWalls.splice(order, 0, { ...wall, name: `외각벽 ${maxOutsideWallIndex}` });
  }

  const rawExits = (data.exits ?? []) as unknown[];
  const exits: Exit[] = [];
  let maxExitIndex = 0;
  const unnamedExits: Array<{ exit: Exit; order: number }> = [];
  for (let i = 0; i < rawExits.length; i++) {
    const entry = rawExits[i];
    if (!isRecord(entry)) {
      throw new Error(`exits[${i}]가 객체가 아닙니다`);
    }
    const parsedName = parseWallName(entry.name);
    if (parsedName) {
      const idx = exitNameIndex(parsedName);
      if (idx !== null) {
        maxExitIndex = Math.max(maxExitIndex, idx);
      }
    }
    const exit: Exit = {
      id: `loaded-exit-${i}`,
      name: parsedName ?? '',
      startX: toFiniteNumber(entry.startX ?? entry.start_x, `exits[${i}].startX`),
      startY: toFiniteNumber(entry.startY ?? entry.start_y, `exits[${i}].startY`),
      endX: toFiniteNumber(entry.endX ?? entry.end_x, `exits[${i}].endX`),
      endY: toFiniteNumber(entry.endY ?? entry.end_y, `exits[${i}].endY`),
    };
    if (parsedName) {
      exits.push(exit);
    } else {
      unnamedExits.push({ exit, order: i });
    }
  }

  for (const { exit, order } of unnamedExits) {
    maxExitIndex += 1;
    exits.splice(order, 0, { ...exit, name: `비상구 ${maxExitIndex}` });
  }

  const pillars: Pillar[] = parseRects(
    data.pillars,
    'pillars',
    /^기둥 (\d+)$/,
    '기둥',
    'loaded-pillar',
  );
  const fabrics: Fabric[] = parseRects(
    data.fabrics,
    'fabrics',
    /^구조물 (\d+)$/,
    '구조물',
    'loaded-fabric',
  );

  const rawTexts = (data.layoutTexts ?? []) as unknown[];
  const layoutTexts = rawTexts.map((entry, i) => {
    if (!isRecord(entry)) {
      throw new Error(`layoutTexts[${i}]가 객체가 아닙니다`);
    }
    return {
      id: `loaded-text-${i}`,
      text: typeof entry.text === 'string' ? entry.text : String(entry.text ?? ''),
      x: toFiniteNumber(entry.x, `layoutTexts[${i}].x`),
      y: toFiniteNumber(entry.y, `layoutTexts[${i}].y`),
    };
  });

  let background: BackgroundImage | null = null;
  if (data.background !== undefined && data.background !== null) {
    if (!isRecord(data.background)) {
      throw new Error('background는 객체이거나 null이어야 합니다');
    }
    const bg = data.background;
    const image = typeof bg.image === 'string' ? bg.image : '';
    if (image === '') {
      throw new Error('background.image가 빈 값입니다');
    }
    const width = toFiniteNumber(bg.width, 'background.width');
    const height = toFiniteNumber(bg.height, 'background.height');
    if (width <= 0 || height <= 0) {
      throw new Error('background.width/height는 0보다 커야 합니다');
    }
    const rawAspect = toFiniteNumber(bg.aspect ?? width / height, 'background.aspect');
    background = {
      id: 'loaded-background',
      image,
      x: toFiniteNumber(bg.x, 'background.x'),
      y: toFiniteNumber(bg.y, 'background.y'),
      width,
      height,
      opacity: Math.min(1, Math.max(0.1, toFiniteNumber(bg.opacity, 'background.opacity'))),
      aspect: rawAspect > 0 ? rawAspect : width / height,
    };
  }

  return {
    name,
    width,
    height,
    walls,
    outsideWalls,
    exits,
    pillars,
    fabrics,
    layoutTexts,
    background,
  };
}

export function parseJson(text: string): DrawingDocument {
  const data: unknown = JSON.parse(text);
  return fromSerialized(data);
}
