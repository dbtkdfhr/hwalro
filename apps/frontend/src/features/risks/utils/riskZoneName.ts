import type { Bounds, DrawingText } from './riskZoneTypes';

type RiskZoneNameDrawing = {
  width: number;
  height: number;
  layoutTexts: DrawingText[];
};

const STORE_DISTANCE_RATIO = 0.15;
const MIN_STORE_DISTANCE = 5;
const MAX_STORE_DISTANCE = 15;
const CENTER_RATIO = 0.2;

function normalizeText(value: string): string {
  return value
    .trim()
    .split(/\r?\n+/)
    .map((part) => part.trim().replace(/\s+/g, ' '))
    .filter(Boolean)
    .join('·');
}

function zoneCenter(bounds: Bounds) {
  return {
    x: bounds.x + bounds.width / 2,
    y: bounds.y + bounds.height / 2,
  };
}

function storeDistanceLimit(drawing: RiskZoneNameDrawing): number {
  const shorterSide = Math.max(0, Math.min(drawing.width, drawing.height));
  return Math.min(
    MAX_STORE_DISTANCE,
    Math.max(MIN_STORE_DISTANCE, shorterSide * STORE_DISTANCE_RATIO),
  );
}

function nearestText(center: { x: number; y: number }, texts: DrawingText[]) {
  return texts
    .map((text) => ({ ...text, normalizedText: normalizeText(text.text) }))
    .filter(
      (text) =>
        text.normalizedText.length > 0 && Number.isFinite(text.x) && Number.isFinite(text.y),
    )
    .map((text) => ({
      ...text,
      distance: Math.hypot(center.x - text.x, center.y - text.y),
    }))
    .sort((left, right) => left.distance - right.distance)[0];
}

function relativeStoreDirection(deltaX: number, deltaY: number): string {
  if (Math.abs(deltaX) >= Math.abs(deltaY)) return deltaX >= 0 ? '동쪽' : '서쪽';
  return deltaY >= 0 ? '남쪽' : '북쪽';
}

function fallbackName(center: { x: number; y: number }, drawing: RiskZoneNameDrawing): string {
  const halfWidth = Math.max(drawing.width / 2, 1);
  const halfHeight = Math.max(drawing.height / 2, 1);
  const normalizedX = (center.x - drawing.width / 2) / halfWidth;
  const normalizedY = (center.y - drawing.height / 2) / halfHeight;

  if (Math.abs(normalizedX) <= CENTER_RATIO && Math.abs(normalizedY) <= CENTER_RATIO) {
    return '중앙 통로';
  }
  if (Math.abs(normalizedX) >= Math.abs(normalizedY)) {
    return normalizedX >= 0 ? '동측 통로' : '서측 통로';
  }
  return normalizedY >= 0 ? '남측 통로' : '북측 통로';
}

/** 도면에 저장된 구역. 이름이 이미 정해져 있으므로 추측보다 우선한다. */
export interface NamedZone {
  name: string;
  rect: { x: number; y: number; width: number; height: number };
}

function containingZone(center: { x: number; y: number }, zones: NamedZone[]): NamedZone | null {
  const matches = zones.filter(
    (zone) =>
      center.x >= zone.rect.x &&
      center.x <= zone.rect.x + zone.rect.width &&
      center.y >= zone.rect.y &&
      center.y <= zone.rect.y + zone.rect.height,
  );
  if (matches.length === 0) {
    return null;
  }
  // 구역이 겹치면 더 좁은 쪽이 더 구체적인 장소다.
  return matches.reduce((narrowest, zone) =>
    zone.rect.width * zone.rect.height < narrowest.rect.width * narrowest.rect.height
      ? zone
      : narrowest,
  );
}

/**
 * 위험 구역의 이름을 짓는다.
 *
 * 도면에 구역이 있으면 그 이름을 쓴다 - 사람이 붙인 이름이 좌표 추측보다 정확하다.
 * 구역이 없는 도면에서는 가장 가까운 매장 텍스트로 추측하고, 그마저 없으면 방위로 부른다.
 */
export function generateRiskZoneName(
  bounds: Bounds,
  drawing: RiskZoneNameDrawing,
  zones: NamedZone[] = [],
): string {
  const center = zoneCenter(bounds);
  const zone = containingZone(center, zones);
  if (zone) {
    return zone.name;
  }
  const store = nearestText(center, drawing.layoutTexts);
  if (store && store.distance > 0 && store.distance <= storeDistanceLimit(drawing)) {
    const storeName = store.normalizedText.endsWith('매장')
      ? store.normalizedText
      : `${store.normalizedText} 매장`;
    const direction = relativeStoreDirection(center.x - store.x, center.y - store.y);
    return `${storeName} ${direction} 통로`;
  }
  return fallbackName(center, drawing);
}
