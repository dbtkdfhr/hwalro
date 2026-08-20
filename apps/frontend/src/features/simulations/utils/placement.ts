import { clamp, distanceToSegment } from '../../layout/utils/geometry';
import type { SimulationDrawing, SimulationPoint, SimulationRect } from '../types';

export const AGENT_RADIUS = 0.3;
export const AGENT_SPACING = AGENT_RADIUS * 2;
export const MAX_AGENTS = 5000;
export const MIN_GEOMETRY_CLEARANCE = AGENT_RADIUS + 0.001;
const EPSILON = 1e-7;
const GRID_SPACING = 0.601;

export function parseHighlightedAgentId(value: string | null, agentCount: number): number | null {
  if (value === null) return null;
  const agentId = Number(value);
  return Number.isInteger(agentId) && agentId >= 1 && agentId <= agentCount ? agentId : null;
}

function squaredDistance(a: SimulationPoint, b: SimulationPoint): number {
  const dx = a.x - b.x;
  const dy = a.y - b.y;
  return dx * dx + dy * dy;
}

export function pointInPolygon(point: SimulationPoint, polygon: SimulationPoint[]): boolean {
  if (polygon.length < 3) return false;
  let inside = false;
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const a = polygon[i];
    const b = polygon[j];
    if (distanceToSegment(point, a, b) <= EPSILON) return true;
    const crosses =
      a.y > point.y !== b.y > point.y &&
      point.x < ((b.x - a.x) * (point.y - a.y)) / (b.y - a.y) + a.x;
    if (crosses) inside = !inside;
  }
  return inside;
}

function hasBoundaryClearance(point: SimulationPoint, boundary: SimulationPoint[]): boolean {
  if (!pointInPolygon(point, boundary)) return false;
  for (let i = 0; i < boundary.length; i += 1) {
    if (
      distanceToSegment(point, boundary[i], boundary[(i + 1) % boundary.length]) + EPSILON <
      MIN_GEOMETRY_CLEARANCE
    ) {
      return false;
    }
  }
  return true;
}

function collidesWithRotatedRect(point: SimulationPoint, rect: SimulationRect): boolean {
  const centerX = (rect.startX + rect.endX) / 2;
  const centerY = (rect.startY + rect.endY) / 2;
  const halfWidth = Math.abs(rect.endX - rect.startX) / 2;
  const halfHeight = Math.abs(rect.endY - rect.startY) / 2;
  const radians = (-rect.rotation * Math.PI) / 180;
  const dx = point.x - centerX;
  const dy = point.y - centerY;
  const localX = dx * Math.cos(radians) - dy * Math.sin(radians);
  const localY = dx * Math.sin(radians) + dy * Math.cos(radians);
  const outsideX = Math.max(Math.abs(localX) - halfWidth, 0);
  const outsideY = Math.max(Math.abs(localY) - halfHeight, 0);
  return Math.hypot(outsideX, outsideY) + EPSILON < MIN_GEOMETRY_CLEARANCE;
}

export function isValidAgentPosition(
  point: SimulationPoint,
  drawing: SimulationDrawing,
  agents: SimulationPoint[] = [],
): boolean {
  if (!Number.isFinite(point.x) || !Number.isFinite(point.y)) return false;
  if (!hasBoundaryClearance(point, drawing.outsideBoundary)) return false;

  const collidesWithLine = (line: { startX: number; startY: number; endX: number; endY: number }) =>
    distanceToSegment(point, { x: line.startX, y: line.startY }, { x: line.endX, y: line.endY }) +
      EPSILON <
    MIN_GEOMETRY_CLEARANCE;
  if (drawing.walls.some(collidesWithLine) || drawing.exits.some(collidesWithLine)) return false;
  if (
    drawing.pillars.some((rect) => collidesWithRotatedRect(point, rect)) ||
    drawing.fabrics.some((rect) => collidesWithRotatedRect(point, rect))
  ) {
    return false;
  }

  const minimumDistanceSquared = AGENT_SPACING * AGENT_SPACING - EPSILON;
  return agents.every((agent) => squaredDistance(point, agent) >= minimumDistanceSquared);
}

function seededRandom(seed: number): () => number {
  let value = seed >>> 0;
  return () => {
    value += 0x6d2b79f5;
    let result = value;
    result = Math.imul(result ^ (result >>> 15), result | 1);
    result ^= result + Math.imul(result ^ (result >>> 7), result | 61);
    return ((result ^ (result >>> 14)) >>> 0) / 4294967296;
  };
}

function rounded(point: SimulationPoint): SimulationPoint {
  return { x: Math.round(point.x * 10000) / 10000, y: Math.round(point.y * 10000) / 10000 };
}

export interface UniformPlacementResult {
  positions: SimulationPoint[];
  capacity: number;
}

export function createUniformPlacement(
  requestedCount: number,
  drawing: SimulationDrawing,
  seed: number,
): UniformPlacementResult {
  const boundary = drawing.outsideBoundary;
  if (boundary.length < 3) return { positions: [], capacity: 0 };
  const minX = Math.min(...boundary.map((point) => point.x));
  const maxX = Math.max(...boundary.map((point) => point.x));
  const minY = Math.min(...boundary.map((point) => point.y));
  const maxY = Math.max(...boundary.map((point) => point.y));
  const rowHeight = GRID_SPACING * (Math.sqrt(3) / 2);
  const candidates: SimulationPoint[] = [];

  let row = 0;
  for (
    let y = minY + MIN_GEOMETRY_CLEARANCE;
    y <= maxY - MIN_GEOMETRY_CLEARANCE + EPSILON;
    y += rowHeight
  ) {
    const offset = row % 2 === 0 ? 0 : GRID_SPACING / 2;
    for (
      let x = minX + MIN_GEOMETRY_CLEARANCE + offset;
      x <= maxX - MIN_GEOMETRY_CLEARANCE + EPSILON;
      x += GRID_SPACING
    ) {
      const candidate = rounded({ x, y });
      if (isValidAgentPosition(candidate, drawing)) candidates.push(candidate);
    }
    row += 1;
  }

  const random = seededRandom(seed);
  for (let i = candidates.length - 1; i > 0; i -= 1) {
    const j = Math.floor(random() * (i + 1));
    [candidates[i], candidates[j]] = [candidates[j], candidates[i]];
  }
  const count = clamp(Math.floor(requestedCount), 0, Math.min(MAX_AGENTS, candidates.length));
  return {
    positions: candidates.slice(0, count),
    capacity: Math.min(MAX_AGENTS, candidates.length),
  };
}

export function sprayAttemptCount(radius: number): number {
  const clampedRadius = clamp(radius, AGENT_RADIUS, 5);
  return clamp(Math.round((clampedRadius / AGENT_SPACING) ** 2), 1, 20);
}

export function addSprayedAgents(
  center: SimulationPoint,
  radius: number,
  drawing: SimulationDrawing,
  existing: SimulationPoint[],
  random: () => number = Math.random,
): SimulationPoint[] {
  if (existing.length >= MAX_AGENTS) return existing;
  const attemptCount = sprayAttemptCount(radius);
  const next = [...existing];
  for (let i = 0; i < attemptCount && next.length < MAX_AGENTS; i += 1) {
    const distance = attemptCount === 1 ? 0 : Math.sqrt(random()) * radius;
    const angle = random() * Math.PI * 2;
    const candidate = rounded({
      x: center.x + Math.cos(angle) * distance,
      y: center.y + Math.sin(angle) * distance,
    });
    if (isValidAgentPosition(candidate, drawing, next)) next.push(candidate);
  }
  return next.length === existing.length ? existing : next;
}

export function eraseAgents(
  center: SimulationPoint,
  radius: number,
  agents: SimulationPoint[],
): SimulationPoint[] {
  const radiusSquared = radius * radius;
  return agents.filter((agent) => squaredDistance(center, agent) > radiusSquared);
}
