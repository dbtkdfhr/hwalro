import { useEffect, useRef } from 'react';

interface MinimapPoint {
  x: number;
  y: number;
}

interface MinimapSegment {
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  rotation?: number;
}

export interface MinimapDrawing {
  width: number;
  height: number;
  outsideBoundary: MinimapPoint[];
  walls: MinimapSegment[];
  exits: MinimapSegment[];
  pillars: MinimapSegment[];
  fabrics: MinimapSegment[];
}

export interface MinimapHighlight {
  x: number;
  y: number;
  width: number;
  height: number;
  label?: string;
}

interface Props {
  drawing: MinimapDrawing;
  highlights: MinimapHighlight[];
  ariaLabel: string;
  width?: number;
  height?: number;
  className?: string;
  responsive?: boolean;
}

const PIXEL_RATIO = 2;
const PADDING = 12;

function strokeSegment(
  context: CanvasRenderingContext2D,
  segment: MinimapSegment,
  scale: number,
  offsetX: number,
  offsetY: number,
) {
  context.beginPath();
  context.moveTo(offsetX + segment.startX * scale, offsetY + segment.startY * scale);
  context.lineTo(offsetX + segment.endX * scale, offsetY + segment.endY * scale);
  context.stroke();
}

function fillRotatedRect(
  context: CanvasRenderingContext2D,
  segment: MinimapSegment,
  scale: number,
  offsetX: number,
  offsetY: number,
) {
  const worldX = Math.min(segment.startX, segment.endX);
  const worldY = Math.min(segment.startY, segment.endY);
  const worldWidth = Math.abs(segment.endX - segment.startX);
  const worldHeight = Math.abs(segment.endY - segment.startY);
  const centerX = offsetX + (worldX + worldWidth / 2) * scale;
  const centerY = offsetY + (worldY + worldHeight / 2) * scale;
  const rotation = ((segment.rotation ?? 0) * Math.PI) / 180;
  context.save();
  context.translate(centerX, centerY);
  context.rotate(rotation);
  context.fillRect(
    (-worldWidth * scale) / 2,
    (-worldHeight * scale) / 2,
    worldWidth * scale,
    worldHeight * scale,
  );
  context.restore();
}

function drawHighlight(
  context: CanvasRenderingContext2D,
  highlight: MinimapHighlight,
  scale: number,
  offsetX: number,
  offsetY: number,
) {
  const x = offsetX + highlight.x * scale;
  const y = offsetY + highlight.y * scale;
  const width = highlight.width * scale;
  const height = highlight.height * scale;
  if (width <= 0 || height <= 0) return;

  context.fillStyle = 'rgba(201, 79, 71, 0.18)';
  context.fillRect(x, y, width, height);
  context.strokeStyle = '#c94f47';
  context.lineWidth = 2;
  context.strokeRect(x, y, width, height);

  if (!highlight.label) return;
  const badgeX = Math.max(10, x);
  const badgeY = Math.max(10, y);
  context.beginPath();
  context.arc(badgeX, badgeY, 9, 0, Math.PI * 2);
  context.fillStyle = '#c94f47';
  context.fill();
  context.fillStyle = '#ffffff';
  context.font = 'bold 10px sans-serif';
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.fillText(highlight.label, badgeX, badgeY + 0.5);
}

export function renderMinimapPlan(
  context: CanvasRenderingContext2D,
  drawing: MinimapDrawing,
  width: number,
  height: number,
  highlights: MinimapHighlight[] = [],
) {
  context.fillStyle = '#ffffff';
  context.fillRect(0, 0, width, height);

  const drawingWidth = Math.max(drawing.width, 1);
  const drawingHeight = Math.max(drawing.height, 1);
  const scale = Math.min(
    (width - PADDING * 2) / drawingWidth,
    (height - PADDING * 2) / drawingHeight,
  );
  const offsetX = (width - drawingWidth * scale) / 2;
  const offsetY = (height - drawingHeight * scale) / 2;

  if (drawing.outsideBoundary.length > 0) {
    context.beginPath();
    context.moveTo(
      offsetX + drawing.outsideBoundary[0].x * scale,
      offsetY + drawing.outsideBoundary[0].y * scale,
    );
    for (const point of drawing.outsideBoundary.slice(1)) {
      context.lineTo(offsetX + point.x * scale, offsetY + point.y * scale);
    }
    context.closePath();
    context.fillStyle = 'rgba(237, 242, 241, 0.6)';
    context.fill();
    context.strokeStyle = '#94a3b8';
    context.lineWidth = 1;
    context.stroke();
  }

  context.strokeStyle = '#475569';
  context.lineWidth = 1.5;
  for (const wall of drawing.walls) strokeSegment(context, wall, scale, offsetX, offsetY);

  context.strokeStyle = '#188e63';
  context.lineWidth = 2;
  for (const exit of drawing.exits) strokeSegment(context, exit, scale, offsetX, offsetY);

  context.fillStyle = 'rgba(148, 163, 184, 0.55)';
  for (const pillar of drawing.pillars) fillRotatedRect(context, pillar, scale, offsetX, offsetY);
  context.fillStyle = 'rgba(203, 213, 225, 0.75)';
  for (const fabric of drawing.fabrics) fillRotatedRect(context, fabric, scale, offsetX, offsetY);

  for (const highlight of highlights) {
    drawHighlight(context, highlight, scale, offsetX, offsetY);
  }
}

export function SimulationMinimap({
  drawing,
  highlights,
  ariaLabel,
  width = 480,
  height = 300,
  className = 'block',
  responsive = false,
}: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const context = canvas.getContext('2d');
    if (!context) return;

    canvas.width = width * PIXEL_RATIO;
    canvas.height = height * PIXEL_RATIO;
    context.setTransform(PIXEL_RATIO, 0, 0, PIXEL_RATIO, 0, 0);
    context.clearRect(0, 0, width, height);
    renderMinimapPlan(context, drawing, width, height, highlights);
  }, [drawing, height, highlights, width]);

  return (
    <canvas
      ref={canvasRef}
      role="img"
      aria-label={ariaLabel}
      className={className}
      style={
        responsive ? { aspectRatio: `${width} / ${height}`, width: '100%' } : { width, height }
      }
    />
  );
}
