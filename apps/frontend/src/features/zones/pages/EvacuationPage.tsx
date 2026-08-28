import { useEffect, useMemo, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent, WheelEvent as ReactWheelEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Layer, Line, Rect, Stage, Text as KonvaText } from 'react-konva';
import { Card, ErrorState, PageHeader, buttonClassName } from '../../../components/ui';
import { drawingApi } from '../../drawings/api/drawingApi';
import type { Drawing } from '../../drawings/types/drawing';
import { getDrawingErrorMessage } from '../../drawings/utils/getDrawingErrorMessage';
import { layoutMetadataApi, type LayoutZone } from '../../layout/api/layoutMetadataApi';
import { CANVAS_COLORS } from '../../layout/utils/colors';
import { clampPan, fitCamera, PX_PER_METER, zoomAtPoint } from '../../layout/utils/geometry';
import { MIN_TEXT_SCREEN_PX, TEXT_FONT_PX } from '../../layout/utils/hitTest';
import type { Camera, Vec2 } from '../../layout/types';
import { zoneApi, type EvacuationRoute, type MyZone } from '../api/zoneApi';
import { EvacuationRouteOverlay } from '../components/EvacuationRouteOverlay';
import { fitEvacuationCamera } from '../utils/evacuationCamera';
import { evacuationStatusPresentation } from '../utils/evacuationStatus';

interface LoadedEvacuation {
  zone: MyZone;
  drawing: Drawing;
  zoneRect: LayoutZone['rect'] | null;
  route: EvacuationRoute;
}

function EvacuationCanvas({
  data,
  width,
  height,
}: {
  data: LoadedEvacuation;
  width: number;
  height: number;
}) {
  const { drawing, zoneRect, route } = data;
  const initialCamera = useMemo(() => {
    const points: Vec2[] = [...route.waypoints, route.routeOrigin];
    if (zoneRect !== null) {
      points.push(
        { x: zoneRect.x, y: zoneRect.y },
        { x: zoneRect.x + zoneRect.width, y: zoneRect.y + zoneRect.height },
      );
    }
    const targetExit = drawing.exits.find((exit) => exit.id === route.recommendedExitId);
    if (targetExit !== undefined) {
      points.push(
        { x: targetExit.startX, y: targetExit.startY },
        { x: targetExit.endX, y: targetExit.endY },
      );
    }
    return points.length > 1
      ? fitEvacuationCamera(points, width, height)
      : fitCamera(drawing.width, drawing.height, width, height);
  }, [drawing, height, route, width, zoneRect]);
  const [camera, setCamera] = useState<Camera>(initialCamera);
  const panRef = useRef<{ screen: Vec2; camera: Camera } | null>(null);

  useEffect(() => setCamera(initialCamera), [initialCamera]);

  const clampCamera = (next: Camera): Camera =>
    clampPan(
      next,
      drawing.width,
      drawing.height,
      width / (next.zoom * PX_PER_METER),
      height / (next.zoom * PX_PER_METER),
    );
  const zoomAtCenter = (factor: number) => {
    setCamera((current) =>
      clampCamera(
        zoomAtPoint(current, { x: width / 2, y: height / 2 }, { left: 0, top: 0 }, factor),
      ),
    );
  };
  const onWheel = (event: ReactWheelEvent<HTMLDivElement>) => {
    event.preventDefault();
    const rect = event.currentTarget.getBoundingClientRect();
    setCamera((current) =>
      clampCamera(
        zoomAtPoint(
          current,
          { x: event.clientX, y: event.clientY },
          rect,
          event.deltaY < 0 ? 1.15 : 1 / 1.15,
        ),
      ),
    );
  };
  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.target instanceof Element && event.target.closest('button') !== null) return;
    if (event.button !== 0 && event.button !== 1) return;
    panRef.current = {
      screen: { x: event.clientX, y: event.clientY },
      camera,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const pan = panRef.current;
    if (pan === null) return;
    setCamera(
      clampCamera({
        ...pan.camera,
        panX: pan.camera.panX - (event.clientX - pan.screen.x) / (pan.camera.zoom * PX_PER_METER),
        panY: pan.camera.panY - (event.clientY - pan.screen.y) / (pan.camera.zoom * PX_PER_METER),
      }),
    );
  };
  const finishPan = () => {
    panRef.current = null;
  };
  const k = camera.zoom * PX_PER_METER;
  // 화면에서 value 픽셀로 보이게 하는 도면 좌표 길이. 레이어 배율이 k이므로 zoom이 아니라 k로 나눈다.
  // zoom으로 나누면 PX_PER_METER배 두꺼워져 벽이 도면을 덮어 버린다.
  const s = (value: number) => value / k;
  return (
    <div
      className="relative h-full w-full cursor-grab touch-none overflow-hidden active:cursor-grabbing"
      onWheel={onWheel}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={finishPan}
      onPointerCancel={finishPan}
      onPointerLeave={finishPan}
    >
      <Stage width={width} height={height}>
        <Layer listening={false} x={-camera.panX * k} y={-camera.panY * k} scaleX={k} scaleY={k}>
          <Rect
            x={0}
            y={0}
            width={drawing.width}
            height={drawing.height}
            fill={CANVAS_COLORS.canvas}
            stroke={CANVAS_COLORS.gridBoundary}
            strokeWidth={s(1)}
          />
          {zoneRect ? (
            <Rect
              x={zoneRect.x}
              y={zoneRect.y}
              width={zoneRect.width}
              height={zoneRect.height}
              fill={CANVAS_COLORS.zoneSelectedFill}
              stroke={CANVAS_COLORS.zoneStroke}
              strokeWidth={s(2)}
            />
          ) : null}
          {drawing.outsideWalls.map((wall, index) => (
            <Line
              key={`outside-${index}`}
              points={[wall.startX, wall.startY, wall.endX, wall.endY]}
              stroke={CANVAS_COLORS.outsideWall}
              strokeWidth={s(3)}
            />
          ))}
          {drawing.walls.map((wall, index) => (
            <Line
              key={`wall-${index}`}
              points={[wall.startX, wall.startY, wall.endX, wall.endY]}
              stroke={CANVAS_COLORS.ink}
              strokeWidth={s(2)}
            />
          ))}
          {drawing.pillars.map((pillar, index) => {
            const width = Math.abs(pillar.endX - pillar.startX);
            const height = Math.abs(pillar.endY - pillar.startY);
            return (
              <Rect
                key={`pillar-${index}`}
                x={(pillar.startX + pillar.endX) / 2}
                y={(pillar.startY + pillar.endY) / 2}
                width={width}
                height={height}
                offsetX={width / 2}
                offsetY={height / 2}
                rotation={pillar.rotation}
                fill={CANVAS_COLORS.pillarFill}
              />
            );
          })}
          {drawing.fabrics.map((fabric, index) => {
            const width = Math.abs(fabric.endX - fabric.startX);
            const height = Math.abs(fabric.endY - fabric.startY);
            return (
              <Rect
                key={`fabric-${index}`}
                x={(fabric.startX + fabric.endX) / 2}
                y={(fabric.startY + fabric.endY) / 2}
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
          {drawing.exits.map((exit, index) => {
            const isRecommended = exit.id !== null && exit.id === route.recommendedExitId;
            const isConfigured = exit.id !== null && exit.id === route.defaultExit?.id;
            return (
              <Line
                key={`exit-${index}`}
                points={[exit.startX, exit.startY, exit.endX, exit.endY]}
                stroke={
                  isRecommended || isConfigured ? CANVAS_COLORS.exitStrong : CANVAS_COLORS.exit
                }
                strokeWidth={s(isRecommended ? 6 : 4)}
                opacity={isConfigured ? 1 : 0.45}
              />
            );
          })}
          <EvacuationRouteOverlay routes={[route]} scale={s} />
        </Layer>
      </Stage>
      <div className="absolute right-3 bottom-3 flex items-center overflow-hidden rounded-lg border border-line bg-surface-elevated/95 shadow-sm">
        <button
          type="button"
          aria-label="축소"
          onClick={() => zoomAtCenter(1 / 1.25)}
          className="h-9 w-9 text-lg text-text-strong hover:bg-panel-soft"
        >
          −
        </button>
        <span className="min-w-14 border-x border-line px-2 text-center text-xs tabular-nums text-text-muted">
          {Math.round(camera.zoom * 100)}%
        </span>
        <button
          type="button"
          aria-label="확대"
          onClick={() => zoomAtCenter(1.25)}
          className="h-9 w-9 text-lg text-text-strong hover:bg-panel-soft"
        >
          +
        </button>
        <button
          type="button"
          onClick={() => setCamera(initialCamera)}
          className="h-9 border-l border-line px-3 text-xs font-bold text-text-strong hover:bg-panel-soft"
        >
          경로 맞춤
        </button>
      </div>
    </div>
  );
}

function EvacuationPage() {
  const { zoneId = '' } = useParams();
  const [data, setData] = useState<LoadedEvacuation | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const [viewport, setViewport] = useState({ width: 0, height: 0 });

  useEffect(() => {
    const element = containerRef.current;
    if (element === null) {
      return;
    }
    const observer = new ResizeObserver((entries) => {
      const { width, height } = entries[0].contentRect;
      setViewport({ width, height });
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, [data]);

  useEffect(() => {
    let active = true;
    const numericZoneId = Number(zoneId);
    async function load() {
      setIsLoading(true);
      setErrorMessage(null);
      try {
        const zones = await zoneApi.myZones();
        const zone = zones.find((entry) => entry.zoneId === numericZoneId);
        if (zone === undefined) {
          throw new Error('담당 구역이 아닙니다.');
        }
        const [drawing, metadata, routes] = await Promise.all([
          drawingApi.get(zone.drawingId),
          layoutMetadataApi.get(zone.drawingId),
          zoneApi.evacuationRoutes(zone.drawingId),
        ]);
        const route = routes.find((entry) => entry.zoneId === numericZoneId);
        if (route === undefined) {
          throw new Error('담당 구역의 대피 경로를 찾을 수 없습니다.');
        }
        if (!active) return;
        setData({
          zone,
          drawing,
          zoneRect: metadata.zones.find((entry) => entry.zoneId === numericZoneId)?.rect ?? null,
          route,
        });
        setErrorMessage(null);
      } catch (error) {
        if (active) {
          setErrorMessage(
            error instanceof Error && error.message === '담당 구역이 아닙니다.'
              ? error.message
              : getDrawingErrorMessage(error),
          );
        }
      } finally {
        if (active) setIsLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [zoneId, retryCount]);

  const presentation =
    data === null
      ? null
      : evacuationStatusPresentation(
          data.route.status,
          data.route.exitChoice,
          data.route.unavailableReason,
        );
  const recommendedName = data?.route.recommendedExitName ?? null;

  return (
    <main className="h-full overflow-hidden bg-background">
      <div className="mx-auto flex h-full w-full max-w-[1600px] flex-col px-1 pt-2 sm:px-4 lg:pt-4">
        <div className="shrink-0 border-b border-line pb-6">
          <PageHeader
            eyebrow="대피 안내"
            title={data?.zone.zoneName ?? '대피 경로'}
            description={data?.zone.drawingTitle ?? ''}
            actions={
              <Link
                to="/my-zones"
                className={buttonClassName({ variant: 'secondary', size: 'sm' })}
              >
                목록으로
              </Link>
            }
          />
        </div>

        {isLoading ? (
          <Card className="mt-4">
            <p className="py-10 text-center text-sm text-text-muted">대피 경로를 불러오는 중...</p>
          </Card>
        ) : errorMessage !== null || data === null || presentation === null ? (
          <Card className="mt-4">
            <ErrorState
              message={errorMessage ?? '대피 경로를 불러오지 못했습니다.'}
              onRetry={() => setRetryCount((count) => count + 1)}
              className="w-full"
            />
          </Card>
        ) : (
          <Card padded={false} className="mt-4 min-h-0 flex-1 overflow-hidden">
            <div
              ref={containerRef}
              role="application"
              aria-label={
                presentation.hasRoute && recommendedName !== null
                  ? `${data.zone.zoneName}에서 ${recommendedName}까지의 대피 경로`
                  : `${data.zone.zoneName}의 도면. 표시할 대피 경로가 없습니다.`
              }
              className="h-full w-full"
            >
              {viewport.width > 0 && viewport.height > 0 ? (
                <EvacuationCanvas data={data} width={viewport.width} height={viewport.height} />
              ) : null}
            </div>
          </Card>
        )}
      </div>
    </main>
  );
}

export default EvacuationPage;
