import { useEffect, useMemo, useRef } from 'react';
import type Konva from 'konva';
import { Circle, Group, Line } from 'react-konva';
import type { EvacuationRoute, RoutePoint } from '../api/zoneApi';
import { CANVAS_COLORS } from '../../layout/utils/colors';

interface EvacuationRouteOverlayProps {
  routes: readonly EvacuationRoute[];
  exitIds?: readonly number[];
  scale: (pixels: number) => number;
}

interface Segment {
  start: RoutePoint;
  end: RoutePoint;
  length: number;
  angle: number;
  accumulatedStart: number;
}

interface PathData {
  key: string;
  waypoints: RoutePoint[];
  segments: Segment[];
  totalLength: number;
}

function interpolatePoint(
  segments: Segment[],
  totalLength: number,
  dist: number,
): { x: number; y: number; angle: number } | null {
  if (segments.length === 0 || totalLength <= 0) return null;
  const wrapped = ((dist % totalLength) + totalLength) % totalLength;

  for (const segment of segments) {
    if (
      wrapped >= segment.accumulatedStart &&
      wrapped <= segment.accumulatedStart + segment.length
    ) {
      const ratio = segment.length > 0 ? (wrapped - segment.accumulatedStart) / segment.length : 0;
      return {
        x: segment.start.x + (segment.end.x - segment.start.x) * ratio,
        y: segment.start.y + (segment.end.y - segment.start.y) * ratio,
        angle: segment.angle,
      };
    }
  }

  const last = segments[segments.length - 1];
  return { x: last.end.x, y: last.end.y, angle: last.angle };
}

export function EvacuationRouteOverlay({ routes, scale }: EvacuationRouteOverlayProps) {
  const markerGroupRef = useRef<Konva.Group>(null);
  const arrowRefs = useRef<Map<string, Konva.Line>>(new Map());

  const paths: PathData[] = useMemo(
    () =>
      routes.flatMap((route) => {
        const branches = [
          ...(route.waypoints.length >= 2
            ? [{ key: `${route.zoneId}-main`, waypoints: route.waypoints }]
            : []),
          ...route.partitions.map((partition) => ({
            key: `${route.zoneId}-${partition.exitId}`,
            waypoints: partition.waypoints,
          })),
        ];

        return branches
          .filter((branch) => branch.waypoints.length >= 2)
          .map((branch) => {
            let accumulated = 0;
            const segments: Segment[] = [];

            for (let index = 0; index < branch.waypoints.length - 1; index += 1) {
              const start = branch.waypoints[index];
              const end = branch.waypoints[index + 1];
              const dx = end.x - start.x;
              const dy = end.y - start.y;
              const length = Math.hypot(dx, dy);
              segments.push({
                start,
                end,
                length,
                angle: Math.atan2(dy, dx),
                accumulatedStart: accumulated,
              });
              accumulated += length;
            }

            return {
              key: branch.key,
              waypoints: branch.waypoints,
              segments,
              totalLength: accumulated,
            };
          });
      }),
    [routes],
  );

  useEffect(() => {
    if (paths.length === 0 || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    let frame = 0;
    const speed = scale(32);
    const startTime = performance.now();

    const animate = (now: number) => {
      const offset = ((now - startTime) / 1000) * speed;

      paths.forEach((path) => {
        if (path.totalLength <= 0) return;
        const step = scale(40);
        const count = Math.max(1, Math.floor(path.totalLength / step));

        for (let index = 0; index < count; index += 1) {
          const arrowKey = `${path.key}-arrow-${index}`;
          const node = arrowRefs.current.get(arrowKey);
          if (!node) continue;

          const distance = (index * (path.totalLength / count) + offset) % path.totalLength;
          const position = interpolatePoint(path.segments, path.totalLength, distance);
          if (position) {
            node.position({ x: position.x, y: position.y });
            node.rotation((position.angle * 180) / Math.PI);
            node.visible(true);
          }
        }
      });

      markerGroupRef.current?.getLayer()?.batchDraw();
      frame = window.requestAnimationFrame(animate);
    };

    frame = window.requestAnimationFrame(animate);
    return () => window.cancelAnimationFrame(frame);
  }, [paths, scale]);

  const registerArrow = (key: string) => (node: Konva.Line | null) => {
    if (node === null) arrowRefs.current.delete(key);
    else arrowRefs.current.set(key, node);
  };

  const arrowSize = scale(6);

  return (
    <Group listening={false}>
      {paths.map((path) => {
        const firstPoint = path.waypoints[0];
        const lastPoint = path.waypoints[path.waypoints.length - 1];
        const step = scale(40);
        const arrowCount = Math.max(1, Math.floor(path.totalLength / step));

        return (
          <Group key={path.key}>
            <Line
              points={path.waypoints.flatMap((point) => [point.x, point.y])}
              stroke={CANVAS_COLORS.accent}
              strokeWidth={scale(10)}
              opacity={0.2}
              lineCap="round"
              lineJoin="round"
            />
            <Line
              points={path.waypoints.flatMap((point) => [point.x, point.y])}
              stroke="#0b1220"
              strokeWidth={scale(5.5)}
              opacity={0.55}
              lineCap="round"
              lineJoin="round"
            />
            <Line
              name="evacuation-route-line"
              points={path.waypoints.flatMap((point) => [point.x, point.y])}
              stroke={CANVAS_COLORS.accent}
              strokeWidth={scale(3.5)}
              opacity={0.95}
              lineCap="round"
              lineJoin="round"
            />
            <Circle
              x={firstPoint.x}
              y={firstPoint.y}
              radius={scale(4.5)}
              fill={CANVAS_COLORS.accent}
              stroke="#ffffff"
              strokeWidth={scale(1.5)}
            />
            <Circle
              x={lastPoint.x}
              y={lastPoint.y}
              radius={scale(5.5)}
              fill="#ffffff"
              stroke={CANVAS_COLORS.accent}
              strokeWidth={scale(2.5)}
            />
            <Group ref={markerGroupRef}>
              {Array.from({ length: arrowCount }, (_, index) => {
                const arrowKey = `${path.key}-arrow-${index}`;
                return (
                  <Line
                    key={arrowKey}
                    ref={registerArrow(arrowKey)}
                    points={[-arrowSize, -arrowSize, 0, 0, -arrowSize, arrowSize]}
                    stroke="#ffffff"
                    strokeWidth={scale(2)}
                    lineCap="round"
                    lineJoin="round"
                    opacity={0.95}
                  />
                );
              })}
            </Group>
          </Group>
        );
      })}
    </Group>
  );
}
