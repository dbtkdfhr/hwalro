import type {
  Exit,
  Fabric,
  LayoutText,
  OutsideWall,
  Pillar,
  RectHandle,
  Vec2,
  Wall,
  WallHandle,
} from '../types';
import {
  distanceToSegment,
  estimateTextWidthPx,
  PX_PER_METER,
  rectCenter,
  rotatePoint,
} from './geometry';

export const HIT_RADIUS_PX = 6;
export const HANDLE_RADIUS_PX = 8;
export const TEXT_FONT_PX = 11;
export const MIN_TEXT_SCREEN_PX = 4;
export const ROTATE_HANDLE_OFFSET_PX = 16;

function pxToWorld(px: number, zoom: number): number {
  return px / (zoom * PX_PER_METER);
}

export interface WorldBox {
  x: number;
  y: number;
  w: number;
  h: number;
}

export function textWorldBox(text: LayoutText): WorldBox {
  const lines = text.text.split('\n');
  const longestLine = lines.reduce((longest, line) =>
    line.length > longest.length ? line : longest,
  );
  return {
    x: text.x,
    y: text.y,
    w: estimateTextWidthPx(longestLine, TEXT_FONT_PX) / PX_PER_METER,
    h: (lines.length * TEXT_FONT_PX) / PX_PER_METER,
  };
}

export function hitTestWall(point: Vec2, wall: Wall, zoom: number): boolean {
  const radius = pxToWorld(HIT_RADIUS_PX, zoom);
  const start = { x: wall.startX, y: wall.startY };
  const end = { x: wall.endX, y: wall.endY };
  return distanceToSegment(point, start, end) <= radius;
}

export function hitTestOutsideWall(point: Vec2, wall: OutsideWall, zoom: number): boolean {
  return hitTestWall(point, wall, zoom);
}

export function hitTestExit(point: Vec2, exit: Exit, zoom: number): boolean {
  const radius = pxToWorld(HIT_RADIUS_PX, zoom);
  const start = { x: exit.startX, y: exit.startY };
  const end = { x: exit.endX, y: exit.endY };
  return distanceToSegment(point, start, end) <= radius;
}

export function hitTestText(point: Vec2, text: LayoutText, zoom: number): boolean {
  if (TEXT_FONT_PX * zoom < MIN_TEXT_SCREEN_PX) {
    return false;
  }
  const box = textWorldBox(text);
  const pad = pxToWorld(HIT_RADIUS_PX, zoom);
  return (
    point.x >= box.x - pad &&
    point.x <= box.x + box.w + pad &&
    point.y >= box.y - pad &&
    point.y <= box.y + box.h + pad
  );
}

export interface RectLike {
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation: number;
}

export function hitTestRect(element: RectLike, point: Vec2, tolerance: number): boolean {
  const center = rectCenter(element);
  const local = rotatePoint(point, center, -element.rotation);
  const minX = Math.min(element.startX, element.endX) - tolerance;
  const minY = Math.min(element.startY, element.endY) - tolerance;
  const maxX = Math.max(element.startX, element.endX) + tolerance;
  const maxY = Math.max(element.startY, element.endY) + tolerance;
  return local.x >= minX && local.x <= maxX && local.y >= minY && local.y <= maxY;
}

export function hitTestPillar(point: Vec2, pillar: Pillar, zoom: number): boolean {
  return hitTestRect(pillar, point, pxToWorld(HIT_RADIUS_PX, zoom));
}

export function hitTestFabric(point: Vec2, fabric: Fabric, zoom: number): boolean {
  return hitTestRect(fabric, point, pxToWorld(HIT_RADIUS_PX, zoom));
}

export interface ElementHit {
  wallId: string | null;
  outsideWallId: string | null;
  exitId: string | null;
  textId: string | null;
  pillarId: string | null;
  fabricId: string | null;
}

export function hitTestElements(
  point: Vec2,
  walls: Wall[],
  outsideWalls: OutsideWall[],
  texts: LayoutText[],
  pillars: Pillar[],
  fabrics: Fabric[],
  exits: Exit[],
  zoom: number,
): ElementHit {
  for (const text of texts) {
    if (hitTestText(point, text, zoom)) {
      return {
        wallId: null,
        outsideWallId: null,
        exitId: null,
        textId: text.id,
        pillarId: null,
        fabricId: null,
      };
    }
  }
  for (let i = fabrics.length - 1; i >= 0; i--) {
    const fabric = fabrics[i];
    if (hitTestFabric(point, fabric, zoom)) {
      return {
        wallId: null,
        outsideWallId: null,
        exitId: null,
        textId: null,
        pillarId: null,
        fabricId: fabric.id,
      };
    }
  }
  for (let i = pillars.length - 1; i >= 0; i--) {
    const pillar = pillars[i];
    if (hitTestPillar(point, pillar, zoom)) {
      return {
        wallId: null,
        outsideWallId: null,
        exitId: null,
        textId: null,
        pillarId: pillar.id,
        fabricId: null,
      };
    }
  }
  for (const exit of exits) {
    if (hitTestExit(point, exit, zoom)) {
      return {
        wallId: null,
        outsideWallId: null,
        exitId: exit.id,
        textId: null,
        pillarId: null,
        fabricId: null,
      };
    }
  }
  for (const wall of walls) {
    if (hitTestWall(point, wall, zoom)) {
      return {
        wallId: wall.id,
        outsideWallId: null,
        exitId: null,
        textId: null,
        pillarId: null,
        fabricId: null,
      };
    }
  }
  for (const wall of outsideWalls) {
    if (hitTestOutsideWall(point, wall, zoom)) {
      return {
        wallId: null,
        outsideWallId: wall.id,
        exitId: null,
        textId: null,
        pillarId: null,
        fabricId: null,
      };
    }
  }
  return {
    wallId: null,
    outsideWallId: null,
    exitId: null,
    textId: null,
    pillarId: null,
    fabricId: null,
  };
}

export interface HandleHit {
  wallId: string;
  handle: WallHandle;
}

export type LineLike = Wall | OutsideWall;

export function hitTestHandle(point: Vec2, wall: LineLike, zoom: number): HandleHit | null {
  const radius = pxToWorld(HANDLE_RADIUS_PX, zoom);
  const start = { x: wall.startX, y: wall.startY };
  const end = { x: wall.endX, y: wall.endY };
  if (Math.hypot(point.x - start.x, point.y - start.y) <= radius) {
    return { wallId: wall.id, handle: 'start' };
  }
  if (Math.hypot(point.x - end.x, point.y - end.y) <= radius) {
    return { wallId: wall.id, handle: 'end' };
  }
  return null;
}

export interface ExitHandleHit {
  exitId: string;
  handle: WallHandle;
}

export function hitTestExitHandle(point: Vec2, exit: Exit, zoom: number): ExitHandleHit | null {
  const radius = pxToWorld(HANDLE_RADIUS_PX, zoom);
  const start = { x: exit.startX, y: exit.startY };
  const end = { x: exit.endX, y: exit.endY };
  if (Math.hypot(point.x - start.x, point.y - start.y) <= radius) {
    return { exitId: exit.id, handle: 'start' };
  }
  if (Math.hypot(point.x - end.x, point.y - end.y) <= radius) {
    return { exitId: exit.id, handle: 'end' };
  }
  return null;
}

export interface RectHandleHit {
  elementId: string;
  handle: RectHandle;
}

export function hitTestRectHandle(
  element: RectLike & { id: string },
  point: Vec2,
  zoom: number,
): RectHandleHit | null {
  const radius = pxToWorld(HANDLE_RADIUS_PX, zoom);
  const center = rectCenter(element);
  const start = rotatePoint({ x: element.startX, y: element.startY }, center, element.rotation);
  const end = rotatePoint({ x: element.endX, y: element.endY }, center, element.rotation);
  if (Math.hypot(point.x - start.x, point.y - start.y) <= radius) {
    return { elementId: element.id, handle: 'start' };
  }
  if (Math.hypot(point.x - end.x, point.y - end.y) <= radius) {
    return { elementId: element.id, handle: 'end' };
  }
  return null;
}

export function rectRotateHandleWorldCenter(element: RectLike, zoom: number): Vec2 {
  const center = rectCenter(element);
  const local = {
    x: center.x,
    y: Math.min(element.startY, element.endY) - pxToWorld(ROTATE_HANDLE_OFFSET_PX, zoom),
  };
  return rotatePoint(local, center, element.rotation);
}

export function hitTestRotateHandle(element: RectLike, point: Vec2, zoom: number): boolean {
  const radius = pxToWorld(HANDLE_RADIUS_PX, zoom);
  const handle = rectRotateHandleWorldCenter(element, zoom);
  return Math.hypot(point.x - handle.x, point.y - handle.y) <= radius;
}
