import type { Vec2 } from '../types';

export const MIN_ZOOM = 0.25;
export const MAX_ZOOM = 32;
export const FIT_MAX_ZOOM = 2;
export const PX_PER_METER = 7;
export const PAN_MARGIN_FACTOR = 0.5;

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

export function clampZoom(zoom: number): number {
  return clamp(zoom, MIN_ZOOM, MAX_ZOOM);
}

export function clampFitZoom(zoom: number): number {
  return clamp(zoom, MIN_ZOOM, FIT_MAX_ZOOM);
}

export function distance(a: Vec2, b: Vec2): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

export function segmentLength(a: Vec2, b: Vec2): number {
  return distance(a, b);
}

export function closestPointOnSegment(p: Vec2, a: Vec2, b: Vec2): Vec2 {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const lenSq = dx * dx + dy * dy;
  if (lenSq === 0) {
    return { x: a.x, y: a.y };
  }
  const t = clamp(((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq, 0, 1);
  return { x: a.x + t * dx, y: a.y + t * dy };
}

export function distanceToSegment(p: Vec2, a: Vec2, b: Vec2): number {
  const closest = closestPointOnSegment(p, a, b);
  return distance(p, closest);
}

export function rotatePoint(p: Vec2, origin: Vec2, angleDeg: number): Vec2 {
  const rad = (angleDeg * Math.PI) / 180;
  const cos = Math.cos(rad);
  const sin = Math.sin(rad);
  const dx = p.x - origin.x;
  const dy = p.y - origin.y;
  return {
    x: origin.x + dx * cos - dy * sin,
    y: origin.y + dx * sin + dy * cos,
  };
}

export function rectCenter(element: {
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}): Vec2 {
  return { x: (element.startX + element.endX) / 2, y: (element.startY + element.endY) / 2 };
}

export function round1(value: number): number {
  return Math.round(value * 10) / 10;
}

export function formatMeters(value: number): string {
  const rounded = round1(value);
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
}

export function screenToWorld(
  screen: Vec2,
  rect: { left: number; top: number },
  camera: CameraLike,
): Vec2 {
  return {
    x: camera.panX + (screen.x - rect.left) / (camera.zoom * PX_PER_METER),
    y: camera.panY + (screen.y - rect.top) / (camera.zoom * PX_PER_METER),
  };
}

export interface CameraLike {
  zoom: number;
  panX: number;
  panY: number;
}

export function worldToScreen(
  world: Vec2,
  rect: { left: number; top: number },
  camera: CameraLike,
): Vec2 {
  return {
    x: rect.left + (world.x - camera.panX) * camera.zoom * PX_PER_METER,
    y: rect.top + (world.y - camera.panY) * camera.zoom * PX_PER_METER,
  };
}

export function zoomAtPoint(
  camera: CameraLike,
  screen: Vec2,
  rect: { left: number; top: number },
  factor: number,
): CameraLike {
  const anchor = screenToWorld(screen, rect, camera);
  const zoom = clampZoom(camera.zoom * factor);
  return {
    zoom,
    panX: anchor.x - (screen.x - rect.left) / (zoom * PX_PER_METER),
    panY: anchor.y - (screen.y - rect.top) / (zoom * PX_PER_METER),
  };
}

export function fitCamera(
  docWidth: number,
  docHeight: number,
  viewW: number,
  viewH: number,
): CameraLike {
  const zoom = clampFitZoom((Math.min(viewW / docWidth, viewH / docHeight) * 0.95) / PX_PER_METER);
  return {
    zoom,
    panX: (docWidth - viewW / (zoom * PX_PER_METER)) / 2,
    panY: (docHeight - viewH / (zoom * PX_PER_METER)) / 2,
  };
}

export function clampPan(
  camera: CameraLike,
  docWidth: number,
  docHeight: number,
  viewW: number,
  viewH: number,
): CameraLike {
  const rangeX = panRange(docWidth, viewW);
  const rangeY = panRange(docHeight, viewH);
  const panX = clamp(camera.panX, rangeX.min, rangeX.max);
  const panY = clamp(camera.panY, rangeY.min, rangeY.max);
  return { zoom: camera.zoom, panX, panY };
}

export function centerCameraOnPoint(
  camera: CameraLike,
  point: Vec2,
  docWidth: number,
  docHeight: number,
  viewportWidth: number,
  viewportHeight: number,
): CameraLike {
  const viewWidth = viewportWidth / (camera.zoom * PX_PER_METER);
  const viewHeight = viewportHeight / (camera.zoom * PX_PER_METER);
  return clampPan(
    {
      zoom: camera.zoom,
      panX: point.x - viewWidth / 2,
      panY: point.y - viewHeight / 2,
    },
    docWidth,
    docHeight,
    viewWidth,
    viewHeight,
  );
}

function panRange(docSize: number, viewSize: number): { min: number; max: number } {
  const margin = viewSize * PAN_MARGIN_FACTOR;
  let min = -margin;
  let max = docSize + margin - viewSize;
  if (viewSize > max - min) {
    const extra = (viewSize - (max - min)) / 2;
    min -= extra;
    max += extra;
  }
  return { min, max };
}

export function estimateTextWidthPx(text: string, fontSizePx: number): number {
  return text.length * fontSizePx * 0.62 + 4;
}
