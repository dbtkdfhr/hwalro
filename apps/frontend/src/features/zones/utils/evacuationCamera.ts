import type { Camera, Vec2 } from '../../layout/types';
import { clamp, MIN_ZOOM, PX_PER_METER } from '../../layout/utils/geometry';

const MAX_EVACUATION_FIT_ZOOM = 4;

export function fitEvacuationCamera(points: Vec2[], viewWidth: number, viewHeight: number): Camera {
  const minX = Math.min(...points.map((point) => point.x));
  const maxX = Math.max(...points.map((point) => point.x));
  const minY = Math.min(...points.map((point) => point.y));
  const maxY = Math.max(...points.map((point) => point.y));
  const contentWidth = Math.max(4, maxX - minX);
  const contentHeight = Math.max(4, maxY - minY);
  const padding = Math.max(2, Math.max(contentWidth, contentHeight) * 0.08);
  const fittedWidth = contentWidth + padding * 2;
  const fittedHeight = contentHeight + padding * 2;
  const zoom = clamp(
    Math.min(viewWidth / fittedWidth, viewHeight / fittedHeight) / PX_PER_METER,
    MIN_ZOOM,
    MAX_EVACUATION_FIT_ZOOM,
  );
  const visibleWidth = viewWidth / (zoom * PX_PER_METER);
  const visibleHeight = viewHeight / (zoom * PX_PER_METER);
  return {
    zoom,
    panX: (minX + maxX) / 2 - visibleWidth / 2,
    panY: (minY + maxY) / 2 - visibleHeight / 2,
  };
}
