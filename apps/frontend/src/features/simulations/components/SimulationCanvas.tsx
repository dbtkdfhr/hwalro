import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent, WheelEvent as ReactWheelEvent } from 'react';
import { Circle, Layer, Line, Rect, Shape, Stage, Text as KonvaText } from 'react-konva';
import type { Camera, Vec2 } from '../../layout/types';
import {
  clampPan,
  fitCamera,
  PX_PER_METER,
  screenToWorld,
  zoomAtPoint,
} from '../../layout/utils/geometry';
import { MIN_TEXT_SCREEN_PX, TEXT_FONT_PX } from '../../layout/utils/hitTest';
import { GridLayer } from '../../layout/components/layers';
import { CANVAS_COLORS } from '../../layout/utils/colors';
import type { EditableHazardZone, SimulationDrawing, SimulationPoint } from '../types';
import { AGENT_RADIUS, pointInPolygon } from '../utils/placement';

export type SimulationTool = 'select' | 'spray' | 'erase' | 'hazard';

interface SimulationCanvasProps {
  drawing: SimulationDrawing;
  agents: SimulationPoint[];
  hazards: EditableHazardZone[];
  editable: boolean;
  tool: SimulationTool;
  brushRadius: number;
  selectedHazardId: string | null;
  selectedExitIds?: readonly number[];
  highlightedExitId?: number | null;
  highlightedAgentId?: number | null;
  recommendedPosition?: SimulationPoint | null;
  onSpray: (point: SimulationPoint) => void;
  onErase: (point: SimulationPoint) => void;
  onCreateHazard: (point: SimulationPoint) => void;
  onMoveHazard: (clientId: string, point: SimulationPoint) => void;
  onResizeHazard: (clientId: string, radius: number) => void;
  onSelectHazard: (clientId: string | null) => void;
  onGestureStart: () => void;
  onGestureEnd: () => void;
}

interface PanSession {
  startScreen: Vec2;
  startCamera: Camera;
}

interface HazardDragSession {
  clientId: string;
}

interface HazardResizeSession {
  clientId: string;
  centerX: number;
  centerY: number;
}

export const HAZARD_MIN_RADIUS = 0.3;
export const HAZARD_MAX_RADIUS = 20;
const HAZARD_RESIZE_HANDLE_PX = 10;

export function SimulationCanvas({
  drawing,
  agents,
  hazards,
  editable,
  tool,
  brushRadius,
  selectedHazardId,
  selectedExitIds = [],
  highlightedExitId = null,
  highlightedAgentId = null,
  recommendedPosition = null,
  onSpray,
  onErase,
  onCreateHazard,
  onMoveHazard,
  onResizeHazard,
  onSelectHazard,
  onGestureStart,
  onGestureEnd,
}: SimulationCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const panRef = useRef<PanSession | null>(null);
  const hazardDragRef = useRef<HazardDragSession | null>(null);
  const hazardResizeRef = useRef<HazardResizeSession | null>(null);
  const intervalRef = useRef<number | null>(null);
  const cursorRef = useRef<SimulationPoint | null>(null);
  const gestureRef = useRef(false);
  const spaceDownRef = useRef(false);
  const [spaceDown, setSpaceDown] = useState(false);
  const [panning, setPanning] = useState(false);
  const [resizingHazard, setResizingHazard] = useState(false);
  const [size, setSize] = useState({ w: 0, h: 0 });
  const [cursor, setCursor] = useState<SimulationPoint | null>(null);
  const [camera, setCamera] = useState<Camera>({ zoom: 1, panX: 0, panY: 0 });

  useEffect(() => {
    const node = containerRef.current;
    if (!node) return;
    const observer = new ResizeObserver(([entry]) => {
      setSize({ w: entry.contentRect.width, h: entry.contentRect.height });
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (size.w > 0 && size.h > 0) {
      setCamera(fitCamera(drawing.width, drawing.height, size.w, size.h) as Camera);
    }
  }, [drawing.height, drawing.width, size.h, size.w]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.code === 'Space' && !event.repeat) {
        spaceDownRef.current = true;
        setSpaceDown(true);
      }
    };
    const onKeyUp = (event: KeyboardEvent) => {
      if (event.code === 'Space') {
        spaceDownRef.current = false;
        setSpaceDown(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
    };
  }, []);

  useEffect(
    () => () => {
      if (intervalRef.current !== null) window.clearInterval(intervalRef.current);
    },
    [],
  );

  const worldAt = useCallback(
    (clientX: number, clientY: number) => {
      const rect = containerRef.current?.getBoundingClientRect();
      return rect ? screenToWorld({ x: clientX, y: clientY }, rect, camera) : null;
    },
    [camera],
  );

  const finishGesture = useCallback(() => {
    if (intervalRef.current !== null) {
      window.clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    hazardDragRef.current = null;
    hazardResizeRef.current = null;
    setResizingHazard(false);
    if (gestureRef.current) {
      gestureRef.current = false;
      onGestureEnd();
    }
  }, [onGestureEnd]);

  const beginGesture = () => {
    if (!gestureRef.current) {
      gestureRef.current = true;
      onGestureStart();
    }
  };

  const beginBrush = (point: SimulationPoint, apply: (point: SimulationPoint) => void) => {
    beginGesture();
    apply(point);
    if (intervalRef.current !== null) window.clearInterval(intervalRef.current);
    intervalRef.current = window.setInterval(() => {
      if (cursorRef.current) apply(cursorRef.current);
    }, 100);
  };

  const beginPan = (event: ReactPointerEvent<HTMLDivElement>) => {
    panRef.current = {
      startScreen: { x: event.clientX, y: event.clientY },
      startCamera: camera,
    };
    setPanning(true);
  };

  const hitHazard = (point: SimulationPoint): EditableHazardZone | null => {
    for (let i = hazards.length - 1; i >= 0; i -= 1) {
      const hazard = hazards[i];
      if (Math.hypot(point.x - hazard.centerX, point.y - hazard.centerY) <= hazard.radius) {
        return hazard;
      }
    }
    return null;
  };

  const hitSelectedHazardResizeHandle = (point: SimulationPoint): EditableHazardZone | null => {
    if (!editable || tool !== 'select' || selectedHazardId === null) return null;
    const hazard = hazards.find((item) => item.clientId === selectedHazardId);
    if (!hazard) return null;
    const handleX = hazard.centerX + hazard.radius;
    const hitRadius = HAZARD_RESIZE_HANDLE_PX / (camera.zoom * PX_PER_METER);
    return Math.hypot(point.x - handleX, point.y - hazard.centerY) <= hitRadius ? hazard : null;
  };

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    const point = worldAt(event.clientX, event.clientY);
    if (!point) return;
    cursorRef.current = point;
    setCursor(point);

    if (event.button === 1 || (event.button === 0 && spaceDownRef.current)) {
      beginPan(event);
      event.currentTarget.setPointerCapture(event.pointerId);
      return;
    }
    if (event.button !== 0) return;
    event.currentTarget.setPointerCapture(event.pointerId);

    if (tool === 'spray') {
      beginBrush(point, onSpray);
    } else if (tool === 'erase') {
      beginBrush(point, onErase);
    } else if (tool === 'hazard') {
      if (pointInPolygon(point, drawing.outsideBoundary)) onCreateHazard(point);
    } else {
      const resizeHit = hitSelectedHazardResizeHandle(point);
      if (resizeHit) {
        beginGesture();
        hazardResizeRef.current = {
          clientId: resizeHit.clientId,
          centerX: resizeHit.centerX,
          centerY: resizeHit.centerY,
        };
        setResizingHazard(true);
        return;
      }
      const hit = hitHazard(point);
      onSelectHazard(hit?.clientId ?? null);
      if (hit) {
        beginGesture();
        hazardDragRef.current = { clientId: hit.clientId };
      } else {
        beginPan(event);
      }
    }
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const point = worldAt(event.clientX, event.clientY);
    if (!point) return;
    cursorRef.current = point;
    setCursor(point);
    if (hazardResizeRef.current) {
      const resize = hazardResizeRef.current;
      const radius = Math.min(
        HAZARD_MAX_RADIUS,
        Math.max(HAZARD_MIN_RADIUS, Math.hypot(point.x - resize.centerX, point.y - resize.centerY)),
      );
      onResizeHazard(resize.clientId, Number(radius.toFixed(1)));
    } else if (panRef.current) {
      const dx = event.clientX - panRef.current.startScreen.x;
      const dy = event.clientY - panRef.current.startScreen.y;
      const start = panRef.current.startCamera;
      setCamera(
        clampPan(
          {
            zoom: start.zoom,
            panX: start.panX - dx / (start.zoom * PX_PER_METER),
            panY: start.panY - dy / (start.zoom * PX_PER_METER),
          },
          drawing.width,
          drawing.height,
          size.w / (start.zoom * PX_PER_METER),
          size.h / (start.zoom * PX_PER_METER),
        ) as Camera,
      );
    } else if (hazardDragRef.current && pointInPolygon(point, drawing.outsideBoundary)) {
      onMoveHazard(hazardDragRef.current.clientId, point);
    }
  };

  const onPointerUp = (event: ReactPointerEvent<HTMLDivElement>) => {
    panRef.current = null;
    setPanning(false);
    finishGesture();
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  const onWheel = (event: ReactWheelEvent<HTMLDivElement>) => {
    event.preventDefault();
    const rect = event.currentTarget.getBoundingClientRect();
    const next = zoomAtPoint(
      camera,
      { x: event.clientX, y: event.clientY },
      rect,
      event.deltaY < 0 ? 1.12 : 1 / 1.12,
    );
    setCamera(
      clampPan(
        next,
        drawing.width,
        drawing.height,
        size.w / (next.zoom * PX_PER_METER),
        size.h / (next.zoom * PX_PER_METER),
      ) as Camera,
    );
  };

  const k = camera.zoom * PX_PER_METER;
  const s = (pixels: number) => pixels / k;
  const boundaryPoints = drawing.outsideBoundary.flatMap((point) => [point.x, point.y]);
  const viewW = size.w > 0 ? size.w / k : 1;
  const viewH = size.h > 0 ? size.h / k : 1;
  const showBrush = cursor && (tool === 'spray' || tool === 'erase');
  const highlightedAgent =
    highlightedAgentId !== null ? (agents[highlightedAgentId - 1] ?? null) : null;
  const selectedHazard =
    selectedHazardId === null
      ? null
      : (hazards.find((hazard) => hazard.clientId === selectedHazardId) ?? null);
  const resizeHandleHovered = cursor !== null && hitSelectedHazardResizeHandle(cursor) !== null;
  const agentShape = useMemo(
    () => (
      <Shape
        fill={CANVAS_COLORS.accent}
        sceneFunc={(context, shape) => {
          context.beginPath();
          for (const agent of agents) {
            context.moveTo(agent.x + AGENT_RADIUS, agent.y);
            context.arc(agent.x, agent.y, AGENT_RADIUS, 0, Math.PI * 2, false);
          }
          context.fillStrokeShape(shape);
        }}
      />
    ),
    [agents],
  );
  const cursorClass =
    resizingHazard || resizeHandleHovered
      ? 'cursor-ew-resize'
      : panning
        ? 'cursor-grabbing'
        : spaceDown
          ? 'cursor-grab'
          : tool === 'select'
            ? 'cursor-grab'
            : 'cursor-crosshair';

  return (
    <div
      ref={containerRef}
      className={`absolute inset-0 overflow-hidden bg-canvas-surround ${cursorClass}`}
      role="application"
      aria-label="시뮬레이션 인원 및 위험 구역 배치 캔버스"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
      onPointerLeave={() => {
        if (!gestureRef.current && !panRef.current) {
          cursorRef.current = null;
          setCursor(null);
        }
      }}
      onWheel={onWheel}
    >
      {size.w > 0 && size.h > 0 && (
        <Stage width={size.w} height={size.h} listening={false}>
          <Layer x={-camera.panX * k} y={-camera.panY * k} scaleX={k} scaleY={k}>
            <GridLayer
              minX={camera.panX}
              minY={camera.panY}
              maxX={camera.panX + viewW}
              maxY={camera.panY + viewH}
              zoom={camera.zoom}
            />
            <Line
              points={boundaryPoints}
              closed
              fill={CANVAS_COLORS.canvas}
              stroke={CANVAS_COLORS.outsideWall}
              strokeWidth={s(2)}
            />
            {drawing.walls.map((wall, index) => (
              <Line
                key={`${wall.name}-${index}`}
                points={[wall.startX, wall.startY, wall.endX, wall.endY]}
                stroke={CANVAS_COLORS.ink}
                strokeWidth={s(2)}
                lineCap="round"
              />
            ))}
            {drawing.pillars.map((pillar, index) => {
              const x = Math.min(pillar.startX, pillar.endX);
              const y = Math.min(pillar.startY, pillar.endY);
              const width = Math.abs(pillar.endX - pillar.startX);
              const height = Math.abs(pillar.endY - pillar.startY);
              return (
                <Rect
                  key={`${pillar.name}-${index}`}
                  x={x + width / 2}
                  y={y + height / 2}
                  width={width}
                  height={height}
                  offsetX={width / 2}
                  offsetY={height / 2}
                  rotation={pillar.rotation}
                  fill={CANVAS_COLORS.pillarFill}
                  stroke={CANVAS_COLORS.pillarStroke}
                  strokeWidth={s(1)}
                />
              );
            })}
            {drawing.fabrics.map((fabric, index) => {
              const x = Math.min(fabric.startX, fabric.endX);
              const y = Math.min(fabric.startY, fabric.endY);
              const width = Math.abs(fabric.endX - fabric.startX);
              const height = Math.abs(fabric.endY - fabric.startY);
              return (
                <Rect
                  key={`${fabric.name}-${index}`}
                  x={x + width / 2}
                  y={y + height / 2}
                  width={width}
                  height={height}
                  offsetX={width / 2}
                  offsetY={height / 2}
                  rotation={fabric.rotation}
                  fill={CANVAS_COLORS.fabricFill}
                  stroke={CANVAS_COLORS.fabricStroke}
                  strokeWidth={s(1)}
                />
              );
            })}
            {TEXT_FONT_PX * camera.zoom >= MIN_TEXT_SCREEN_PX &&
              drawing.layoutTexts.map((text, index) => (
                <KonvaText
                  key={`${text.text}-${index}`}
                  x={text.x}
                  y={text.y}
                  text={text.text}
                  fontSize={TEXT_FONT_PX / PX_PER_METER}
                  fill={CANVAS_COLORS.ink}
                  listening={false}
                />
              ))}
            {drawing.exits.map((exit) => {
              const highlighted = exit.id === highlightedExitId;
              const selected = selectedExitIds.includes(exit.id);
              return (
                <Line
                  key={exit.id}
                  points={[exit.startX, exit.startY, exit.endX, exit.endY]}
                  stroke={
                    highlighted
                      ? '#f59e0b'
                      : selected
                        ? CANVAS_COLORS.exitStrong
                        : CANVAS_COLORS.outsideWall
                  }
                  strokeWidth={s(highlighted ? 8 : selected ? 7 : 5)}
                  lineCap="round"
                  shadowColor={highlighted ? '#fbbf24' : CANVAS_COLORS.exit}
                  shadowBlur={highlighted ? s(18) : selected ? s(10) : 0}
                  shadowOpacity={highlighted ? 0.9 : selected ? 0.6 : 0}
                  shadowEnabled={highlighted || selected}
                />
              );
            })}
          </Layer>
          <Layer x={-camera.panX * k} y={-camera.panY * k} scaleX={k} scaleY={k}>
            {agentShape}
          </Layer>
          <Layer x={-camera.panX * k} y={-camera.panY * k} scaleX={k} scaleY={k}>
            {hazards.map((hazard) => (
              <Circle
                key={hazard.clientId}
                x={hazard.centerX}
                y={hazard.centerY}
                radius={hazard.radius}
                fillRadialGradientStartPoint={{ x: 0, y: 0 }}
                fillRadialGradientStartRadius={0}
                fillRadialGradientEndPoint={{ x: 0, y: 0 }}
                fillRadialGradientEndRadius={hazard.radius}
                fillRadialGradientColorStops={[
                  0,
                  'rgba(177, 32, 32, 0.58)',
                  0.5,
                  'rgba(225, 75, 75, 0.28)',
                  1,
                  'rgba(239, 119, 119, 0.08)',
                ]}
                stroke={hazard.clientId === selectedHazardId ? '#d14343' : '#ef7777'}
                strokeWidth={s(hazard.clientId === selectedHazardId ? 2 : 1.3)}
                dash={[s(5), s(4)]}
              />
            ))}
            {editable && selectedHazard && (
              <>
                <Circle
                  x={selectedHazard.centerX + selectedHazard.radius}
                  y={selectedHazard.centerY}
                  radius={s(HAZARD_RESIZE_HANDLE_PX)}
                  fill="#ffffff"
                  stroke="#d14343"
                  strokeWidth={s(2)}
                  shadowColor="#7f1d1d"
                  shadowBlur={s(8)}
                  shadowOpacity={0.28}
                />
                <Circle
                  x={selectedHazard.centerX + selectedHazard.radius}
                  y={selectedHazard.centerY}
                  radius={s(3)}
                  fill="#d14343"
                />
              </>
            )}
            {showBrush && (
              <Circle
                x={cursor.x}
                y={cursor.y}
                radius={brushRadius}
                fill={tool === 'spray' ? 'rgba(22, 143, 128, 0.08)' : 'rgba(201, 79, 71, 0.08)'}
                stroke={tool === 'spray' ? '#168f80' : '#c94f47'}
                strokeWidth={s(1.5)}
                dash={[s(5), s(4)]}
              />
            )}
          </Layer>
          {highlightedAgent && (
            <Layer x={-camera.panX * k} y={-camera.panY * k} scaleX={k} scaleY={k}>
              {recommendedPosition && (
                <>
                  <Line
                    points={[
                      highlightedAgent.x,
                      highlightedAgent.y,
                      recommendedPosition.x,
                      recommendedPosition.y,
                    ]}
                    stroke="#2563eb"
                    strokeWidth={s(2)}
                    dash={[s(6), s(4)]}
                  />
                  <Circle
                    x={recommendedPosition.x}
                    y={recommendedPosition.y}
                    radius={AGENT_RADIUS + s(7)}
                    stroke="#2563eb"
                    strokeWidth={s(2)}
                    dash={[s(6), s(4)]}
                  />
                  <KonvaText
                    x={recommendedPosition.x + s(10)}
                    y={recommendedPosition.y - s(18)}
                    text="추천"
                    fontSize={s(12)}
                    fontStyle="bold"
                    fill="#1d4ed8"
                  />
                </>
              )}
              <Circle
                x={highlightedAgent.x}
                y={highlightedAgent.y}
                radius={AGENT_RADIUS + s(5)}
                stroke="#d97706"
                strokeWidth={s(2)}
                dash={[s(5), s(3)]}
              />
              <Circle
                x={highlightedAgent.x}
                y={highlightedAgent.y}
                radius={AGENT_RADIUS + s(11)}
                stroke="#d97706"
                strokeWidth={s(2)}
                dash={[s(5), s(3)]}
              />
              <KonvaText
                x={highlightedAgent.x + s(12)}
                y={highlightedAgent.y - s(20)}
                text={`! #${highlightedAgentId}`}
                fontSize={s(13)}
                fontStyle="bold"
                fill="#b45309"
              />
            </Layer>
          )}
        </Stage>
      )}
      {highlightedAgent && (
        <p className="sr-only" aria-live="polite">
          에이전트 #{highlightedAgentId}, 현재 위치 x {highlightedAgent.x.toFixed(2)}미터, y{' '}
          {highlightedAgent.y.toFixed(2)}미터
          {recommendedPosition
            ? `, 추천 위치 x ${recommendedPosition.x.toFixed(2)}미터, y ${recommendedPosition.y.toFixed(2)}미터, 이동 거리 ${Math.hypot(recommendedPosition.x - highlightedAgent.x, recommendedPosition.y - highlightedAgent.y).toFixed(2)}미터`
            : ''}
        </p>
      )}
      <div className="simulation-setup-zoom-status">
        <span>확대</span>
        <strong>{Math.round(camera.zoom * 100)}%</strong>
      </div>
    </div>
  );
}
