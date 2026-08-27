import { Suspense, lazy, useEffect, useMemo, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent, WheelEvent as ReactWheelEvent } from 'react';
import {
  applyPixiCamera,
  createCameraTransform,
  createPixiSimulationScene,
  destroyPixiSimulationScene,
  pixiScreenToWorld,
  updatePixiSimulationScene,
  type PixiSimulationScene,
} from '../rendering/pixiSimulationRenderer';
import type { SimulationViewMode } from '../rendering/simulationViewMode';
import {
  savePlanCamera,
  type PlanCameraState,
  type SimulationCameraMemory,
} from '../rendering/simulationCameraMemory';
import type { DetectedBottleneck, RiskZone, SimulationResultViewModel } from '../types';
import './SimulationPlaybackStage.css';

export interface SimulationPlaybackStageProps {
  cameraMemory: SimulationCameraMemory;
  result: SimulationResultViewModel;
  bottlenecks: DetectedBottleneck[];
  currentTimeSeconds: number;
  selectedBottleneckId: number | null;
  showBottlenecks: boolean;
  riskZones: RiskZone[];
  viewMode: SimulationViewMode;
  improvedFabricIndexes?: readonly number[];
  onViewportPan: () => void;
}

const ThreeSimulationStage = lazy(() =>
  import('./ThreeSimulationStage').then((module) => ({ default: module.ThreeSimulationStage })),
);

interface PanSession {
  pointerX: number;
  pointerY: number;
  panX: number;
  panY: number;
  notified: boolean;
}

export function ThreeSimulationLoading() {
  return (
    <div className="simulation-canvas-wrap simulation-three-loading" role="status">
      3D 공간을 준비하고 있습니다.
    </div>
  );
}

export function SimulationPlaybackStage(props: SimulationPlaybackStageProps) {
  if (props.viewMode === 'three') {
    return (
      <Suspense fallback={<ThreeSimulationLoading />}>
        <ThreeSimulationStage
          cameraMemory={props.cameraMemory}
          result={props.result}
          bottlenecks={props.bottlenecks}
          currentTimeSeconds={props.currentTimeSeconds}
          selectedBottleneckId={props.selectedBottleneckId}
          showBottlenecks={props.showBottlenecks}
          riskZones={props.riskZones}
          onViewportPan={props.onViewportPan}
        />
      </Suspense>
    );
  }

  return <PixiSimulationStage {...props} />;
}

function PixiSimulationStage(props: SimulationPlaybackStageProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const sceneRef = useRef<PixiSimulationScene | null>(null);
  const panSessionRef = useRef<PanSession | null>(null);
  const [size, setSize] = useState({ width: 1, height: 1 });
  const [camera, setCamera] = useState<PlanCameraState>(() => ({ ...props.cameraMemory.plan }));
  const [isPanning, setIsPanning] = useState(false);
  const [sceneVersion, setSceneVersion] = useState(0);
  const [sceneError, setSceneError] = useState(false);
  const [sceneRetry, setSceneRetry] = useState(0);

  const transform = useMemo(
    () =>
      createCameraTransform(
        size.width,
        size.height,
        props.result.drawing.width,
        props.result.drawing.height,
        camera.zoom,
        camera.panX,
        camera.panY,
      ),
    [camera, props.result.drawing.height, props.result.drawing.width, size],
  );

  const commitCamera = (nextCamera: PlanCameraState) => {
    savePlanCamera(props.cameraMemory, nextCamera);
    setCamera(nextCamera);
  };

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const observer = new ResizeObserver(([entry]) => {
      setSize({ width: entry.contentRect.width, height: entry.contentRect.height });
      sceneRef.current?.app.renderer.resize(entry.contentRect.width, entry.contentRect.height);
    });
    observer.observe(host);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    let disposed = false;
    let createdScene: PixiSimulationScene | null = null;
    setSceneError(false);
    void createPixiSimulationScene(host, props.result, props.improvedFabricIndexes)
      .then((scene) => {
        if (disposed) {
          destroyPixiSimulationScene(scene);
          return;
        }
        createdScene = scene;
        sceneRef.current = scene;
        setSceneVersion((version) => version + 1);
      })
      .catch((error: unknown) => {
        if (disposed) return;
        console.error('Pixi simulation scene initialization failed.', error);
        setSceneError(true);
      });
    return () => {
      disposed = true;
      if (createdScene) destroyPixiSimulationScene(createdScene);
      if (sceneRef.current === createdScene) sceneRef.current = null;
    };
  }, [
    props.improvedFabricIndexes,
    props.result.drawing,
    props.result.hazardZones,
    props.result.simulationId,
    props.result.totalPeople,
    sceneRetry,
  ]);

  useEffect(() => {
    if (!sceneRef.current) return;
    applyPixiCamera(sceneRef.current, transform);
  }, [sceneVersion, transform]);

  useEffect(() => {
    if (!sceneRef.current) return;
    updatePixiSimulationScene(
      sceneRef.current,
      props.result,
      props.bottlenecks,
      props.currentTimeSeconds,
      props.selectedBottleneckId,
      props.showBottlenecks,
      props.riskZones,
    );
  }, [
    props.bottlenecks,
    props.currentTimeSeconds,
    props.result,
    props.riskZones,
    props.selectedBottleneckId,
    props.showBottlenecks,
    sceneVersion,
  ]);

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (sceneError) return;
    panSessionRef.current = {
      pointerX: event.clientX,
      pointerY: event.clientY,
      panX: camera.panX,
      panY: camera.panY,
      notified: false,
    };
    setIsPanning(true);
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const session = panSessionRef.current;
    if (!session) return;
    const deltaX = event.clientX - session.pointerX;
    const deltaY = event.clientY - session.pointerY;
    if (!session.notified && Math.hypot(deltaX, deltaY) >= 3) {
      session.notified = true;
      props.onViewportPan();
    }
    commitCamera({
      ...camera,
      panX: session.panX + deltaX,
      panY: session.panY + deltaY,
    });
  };

  const onPointerUp = () => {
    if (panSessionRef.current) {
      panSessionRef.current = null;
      setIsPanning(false);
    }
  };

  const onWheel = (event: ReactWheelEvent<HTMLDivElement>) => {
    event.preventDefault();
    const rect = event.currentTarget.getBoundingClientRect();
    const pointerX = event.clientX - rect.left;
    const pointerY = event.clientY - rect.top;
    const world = pixiScreenToWorld(pointerX, pointerY, transform);
    const zoomFactor = Math.exp(-event.deltaY * 0.0012);
    const nextZoom = Math.min(4, Math.max(0.55, camera.zoom * zoomFactor));
    const fitTransform = createCameraTransform(
      size.width,
      size.height,
      props.result.drawing.width,
      props.result.drawing.height,
      nextZoom,
      0,
      0,
    );
    commitCamera({
      zoom: nextZoom,
      panX: pointerX - world.x * fitTransform.scale - fitTransform.offsetX,
      panY: pointerY - world.y * fitTransform.scale - fitTransform.offsetY,
    });
  };

  return (
    <div
      ref={hostRef}
      className={`simulation-canvas-wrap simulation-canvas--interactive is-pannable ${isPanning ? 'is-panning' : ''}`}
      role="application"
      aria-label="시뮬레이션 재생 도면, 2D 보기"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
      onWheel={onWheel}
    >
      {sceneError && (
        <div className="simulation-canvas-error" role="alert" aria-live="assertive">
          <strong>시뮬레이션 화면을 불러오지 못했습니다.</strong>
          <span>그래픽 화면을 초기화하는 중 문제가 발생했습니다.</span>
          <button type="button" onClick={() => setSceneRetry((retry) => retry + 1)}>
            다시 시도
          </button>
        </div>
      )}
    </div>
  );
}
