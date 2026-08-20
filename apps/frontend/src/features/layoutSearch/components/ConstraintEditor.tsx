import { useCallback, useEffect, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { Group, Layer, Line, Rect, Stage, Text as KonvaText } from 'react-konva';
import { GridLayer } from '../../layout/components/layers';
import type { Camera, Vec2 } from '../../layout/types';
import {
  clampPan,
  estimateTextWidthPx,
  fitCamera,
  PX_PER_METER,
  rotatePoint,
  screenToWorld,
  zoomAtPoint,
} from '../../layout/utils/geometry';
import { CANVAS_COLORS } from '../../layout/utils/colors';
import type { SimulationDrawing, SimulationPoint, SimulationRect } from '../../simulations/types';
import type { ForbiddenZone, SearchConstraints } from '../api/layoutSearchApi';

export type ConstraintEditorTool = 'select' | 'zone';

const MIN_ZONE_SIZE_METERS = 0.2;

interface ConstraintEditorProps {
  drawing: SimulationDrawing;
  constraints: SearchConstraints;
  selectedFabricId: number | null;
  tool: ConstraintEditorTool;
  onSelectFabric: (id: number | null) => void;
  onAddForbiddenZone: (zone: ForbiddenZone) => void;
  onMoveForbiddenZone: (index: number, dx: number, dy: number) => void;
  selectedZoneIndex: number | null;
  onSelectZone: (index: number | null) => void;
}

interface PanSession {
  startScreen: Vec2;
  startCamera: Camera;
}

interface ZoneDragSession {
  start: SimulationPoint;
  current: SimulationPoint;
}

function normalizeZone(a: SimulationPoint, b: SimulationPoint): ForbiddenZone {
  return {
    x: Math.min(a.x, b.x),
    y: Math.min(a.y, b.y),
    width: Math.abs(a.x - b.x),
    height: Math.abs(a.y - b.y),
  };
}

function hitZoneIndex(zones: readonly ForbiddenZone[], point: SimulationPoint): number | null {
  for (let i = zones.length - 1; i >= 0; i -= 1) {
    const zone = zones[i];
    if (
      point.x >= zone.x &&
      point.x <= zone.x + zone.width &&
      point.y >= zone.y &&
      point.y <= zone.y + zone.height
    ) {
      return i;
    }
  }
  return null;
}

function hitFabric(
  fabrics: readonly SimulationRect[],
  point: SimulationPoint,
  tolerance = 0.2,
): SimulationRect | null {
  for (let i = fabrics.length - 1; i >= 0; i -= 1) {
    const fabric = fabrics[i];
    const centerX = (fabric.startX + fabric.endX) / 2;
    const centerY = (fabric.startY + fabric.endY) / 2;
    const rotation = fabric.rotation ?? 0;

    const local = rotatePoint(point, { x: centerX, y: centerY }, -rotation);
    const minX = Math.min(fabric.startX, fabric.endX) - tolerance;
    const maxX = Math.max(fabric.startX, fabric.endX) + tolerance;
    const minY = Math.min(fabric.startY, fabric.endY) - tolerance;
    const maxY = Math.max(fabric.startY, fabric.endY) + tolerance;
    if (local.x >= minX && local.x <= maxX && local.y >= minY && local.y <= maxY) {
      return fabric;
    }
  }
  return null;
}

export function ConstraintEditor({
  drawing,
  constraints,
  selectedFabricId,
  tool,
  onSelectFabric,
  onAddForbiddenZone,
  onMoveForbiddenZone,
  selectedZoneIndex,
  onSelectZone,
}: ConstraintEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const panRef = useRef<PanSession | null>(null);
  const zoneDragRef = useRef<ZoneDragSession | null>(null);
  const spaceDownRef = useRef(false);
  const [spaceDown, setSpaceDown] = useState(false);
  const [size, setSize] = useState({ w: 0, h: 0 });
  const [camera, setCamera] = useState<Camera>({ zoom: 1, panX: 0, panY: 0 });
  const [zoneDrag, setZoneDrag] = useState<ZoneDragSession | null>(null);
  const zoneMoveRef = useRef<{ index: number; last: SimulationPoint } | null>(null);
  const cameraInitializedRef = useRef(false);

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
    if (size.w > 0 && size.h > 0 && !cameraInitializedRef.current) {
      cameraInitializedRef.current = true;
      setCamera(fitCamera(drawing.width, drawing.height, size.w, size.h) as Camera);
    }
  }, [drawing.height, drawing.width, size.h, size.w]);

  const cameraRef = useRef(camera);
  useEffect(() => {
    cameraRef.current = camera;
  }, [camera]);
  const sizeRef = useRef(size);
  useEffect(() => {
    sizeRef.current = size;
  }, [size]);
  const drawingRef = useRef(drawing);
  useEffect(() => {
    drawingRef.current = drawing;
  }, [drawing]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return;
    }
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const rect = el.getBoundingClientRect();
      const current = cameraRef.current;
      const next = zoomAtPoint(
        current,
        { x: event.clientX, y: event.clientY },
        rect,
        event.deltaY < 0 ? 1.12 : 1 / 1.12,
      );
      setCamera(
        clampPan(
          next,
          drawingRef.current.width,
          drawingRef.current.height,
          sizeRef.current.w / (next.zoom * PX_PER_METER),
          sizeRef.current.h / (next.zoom * PX_PER_METER),
        ) as Camera,
      );
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, []);

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

  const worldAt = useCallback(
    (clientX: number, clientY: number) => {
      const rect = containerRef.current?.getBoundingClientRect();
      return rect ? screenToWorld({ x: clientX, y: clientY }, rect, camera) : null;
    },
    [camera],
  );

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    const point = worldAt(event.clientX, event.clientY);
    if (!point) return;
    if (event.button === 1 || (event.button === 0 && spaceDownRef.current)) {
      panRef.current = {
        startScreen: { x: event.clientX, y: event.clientY },
        startCamera: camera,
      };
      event.currentTarget.setPointerCapture(event.pointerId);
      return;
    }
    if (event.button !== 0) return;
    event.currentTarget.setPointerCapture(event.pointerId);

    const tolerance = Math.max(0.2, 8 / ((camera.zoom || 1) * PX_PER_METER));

    if (tool === 'zone') {
      const existing = hitZoneIndex(constraints.forbiddenZones, point);
      if (existing !== null) {
        onSelectZone(existing);
        onSelectFabric(null);
        return;
      }
      const session = { start: point, current: point };
      zoneDragRef.current = session;
      setZoneDrag(session);
      return;
    }

    const hit = hitFabric(drawing.fabrics, point, tolerance);
    if (hit !== null) {
      onSelectFabric(hit.id);
      onSelectZone(null);
      return;
    }

    const zoneIndex = hitZoneIndex(constraints.forbiddenZones, point);
    if (zoneIndex !== null) {
      onSelectZone(zoneIndex);
      onSelectFabric(null);
      zoneMoveRef.current = { index: zoneIndex, last: point };
      return;
    }

    onSelectZone(null);
    onSelectFabric(null);
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const point = worldAt(event.clientX, event.clientY);
    if (!point) return;
    if (panRef.current) {
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
      return;
    }
    if (zoneDragRef.current) {
      const next = { ...zoneDragRef.current, current: point };
      zoneDragRef.current = next;
      setZoneDrag(next);
      return;
    }
    if (zoneMoveRef.current) {
      const move = zoneMoveRef.current;
      const dx = point.x - move.last.x;
      const dy = point.y - move.last.y;
      move.last = point;
      if (dx !== 0 || dy !== 0) {
        onMoveForbiddenZone(move.index, dx, dy);
      }
    }
  };

  const onPointerUp = (event: ReactPointerEvent<HTMLDivElement>) => {
    const session = zoneDragRef.current;
    zoneDragRef.current = null;
    setZoneDrag(null);
    if (session) {
      const zone = normalizeZone(session.start, session.current);
      if (zone.width >= MIN_ZONE_SIZE_METERS && zone.height >= MIN_ZONE_SIZE_METERS) {
        onAddForbiddenZone(zone);
      }
    }
    zoneMoveRef.current = null;
    panRef.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  const k = camera.zoom * PX_PER_METER;
  const s = (pixels: number) => pixels / k;
  const boundaryPoints = drawing.outsideBoundary.flatMap((point) => [point.x, point.y]);
  const viewW = size.w > 0 ? size.w / k : 1;
  const viewH = size.h > 0 ? size.h / k : 1;
  const cursorClass = panRef.current
    ? 'is-panning'
    : spaceDown
      ? 'is-space'
      : tool === 'zone'
        ? 'is-zone'
        : 'is-select';

  return (
    <div
      ref={containerRef}
      className={`constraint-editor__viewport ${cursorClass}`}
      role="application"
      aria-label="구조물 제약 설정 캔버스"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
    >
      {' '}
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
              fill="#ffffff"
              stroke="#355b55"
              strokeWidth={s(2)}
            />
            {drawing.walls.map((wall, index) => (
              <Line
                key={`${wall.name}-${index}`}
                points={[wall.startX, wall.startY, wall.endX, wall.endY]}
                stroke="#506663"
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
                  strokeWidth={s(1.2)}
                />
              );
            })}

            {drawing.exits.map((exit) => (
              <Line
                key={exit.id}
                points={[exit.startX, exit.startY, exit.endX, exit.endY]}
                stroke="#00a887"
                strokeWidth={s(4)}
                lineCap="round"
              />
            ))}
            {drawing.fabrics.map((fabric) => {
              const x = Math.min(fabric.startX, fabric.endX);
              const y = Math.min(fabric.startY, fabric.endY);
              const width = Math.abs(fabric.endX - fabric.startX);
              const height = Math.abs(fabric.endY - fabric.startY);
              const centerX = x + width / 2;
              const centerY = y + height / 2;
              const selected = fabric.id === selectedFabricId;
              const moveRadius = constraints.moveRadii[fabric.id];
              const rotationAllowed = constraints.rotationAllowed[fabric.id] ?? true;
              const wallAnchored = constraints.wallAnchored[fabric.id] === true;
              let constraintStroke = '#a0afac';
              let constraintDash: number[] | undefined;
              if (moveRadius === 0) {
                constraintStroke = '#8a5a2b';
              } else if (rotationAllowed === false) {
                constraintStroke = '#e6664e';
                constraintDash = [s(6), s(4)];
              } else if (wallAnchored) {
                constraintStroke = '#148b7c';
                constraintDash = [s(2), s(3)];
              }
              const label = fabric.name || `구조물 ${fabric.id}`;
              return (
                <Group key={fabric.id}>
                  {typeof moveRadius === 'number' && moveRadius > 0 && (
                    <Group x={centerX} y={centerY} rotation={fabric.rotation}>
                      <Rect
                        x={-(width / 2 + moveRadius)}
                        y={-(height / 2 + moveRadius)}
                        width={width + moveRadius * 2}
                        height={height + moveRadius * 2}
                        cornerRadius={moveRadius}
                        stroke="#148b7c"
                        strokeWidth={s(1)}
                        dash={[s(5), s(4)]}
                        fill="rgba(20,139,124,0.06)"
                      />
                    </Group>
                  )}
                  <Rect
                    x={centerX}
                    y={centerY}
                    width={width}
                    height={height}
                    offsetX={width / 2}
                    offsetY={height / 2}
                    rotation={fabric.rotation}
                    fill={selected ? '#d5efe8' : '#e8efed'}
                    stroke={selected ? '#148b7c' : constraintStroke}
                    strokeWidth={s(selected ? 2.5 : 1.5)}
                    dash={constraintDash}
                  />
                  {selected && (
                    <KonvaText
                      x={centerX}
                      y={centerY}
                      offsetX={estimateTextWidthPx(label, s(11)) / 2}
                      offsetY={s(11) / 2}
                      text={label}
                      fontSize={s(11)}
                      fill="#17352d"
                    />
                  )}
                </Group>
              );
            })}
            {constraints.forbiddenZones.map((zone, index) => {
              const selected = index === selectedZoneIndex;
              return (
                <Group key={`${zone.x}-${zone.y}-${index}`}>
                  <Rect
                    x={zone.x}
                    y={zone.y}
                    width={zone.width}
                    height={zone.height}
                    fill={selected ? 'rgba(230,102,78,0.22)' : 'rgba(230,102,78,0.12)'}
                    stroke={selected ? '#c9402a' : '#df624b'}
                    strokeWidth={s(1.5)}
                    dash={[s(4), s(3)]}
                  />
                  {selected && (
                    <KonvaText
                      x={zone.x + s(2)}
                      y={zone.y + s(2)}
                      text="금지 영역"
                      fontSize={s(9)}
                      fill="#b34632"
                    />
                  )}
                </Group>
              );
            })}
            {zoneDrag && (
              <Rect
                x={Math.min(zoneDrag.start.x, zoneDrag.current.x)}
                y={Math.min(zoneDrag.start.y, zoneDrag.current.y)}
                width={Math.abs(zoneDrag.current.x - zoneDrag.start.x)}
                height={Math.abs(zoneDrag.current.y - zoneDrag.start.y)}
                fill="rgba(230,102,78,0.2)"
                stroke="#df624b"
                strokeWidth={s(1.5)}
                dash={[s(4), s(3)]}
              />
            )}
          </Layer>
        </Stage>
      )}
    </div>
  );
}
