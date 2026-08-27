import { useEffect, useRef, useState } from 'react';
import type { DetectedBottleneck, RiskZone, SimulationResultViewModel } from '../types';
import {
  createThreeSimulationScene,
  destroyThreeSimulationScene,
  resizeThreeSimulationScene,
  updateThreeSimulationScene,
  type ThreeSimulationScene,
} from '../rendering/threeSimulationRenderer';
import { saveThreeCamera, type SimulationCameraMemory } from '../rendering/simulationCameraMemory';

interface Props {
  cameraMemory: SimulationCameraMemory;
  result: SimulationResultViewModel;
  bottlenecks: DetectedBottleneck[];
  currentTimeSeconds: number;
  selectedBottleneckId: number | null;
  showBottlenecks: boolean;
  riskZones: RiskZone[];
  onViewportPan: () => void;
}

export function ThreeSimulationStage(props: Props) {
  const hostRef = useRef<HTMLDivElement>(null);
  const sceneRef = useRef<ThreeSimulationScene | null>(null);
  const onViewportPanRef = useRef(props.onViewportPan);
  const [sceneError, setSceneError] = useState(false);
  const [sceneVersion, setSceneVersion] = useState(0);
  const [sceneRetry, setSceneRetry] = useState(0);

  useEffect(() => {
    onViewportPanRef.current = props.onViewportPan;
  }, [props.onViewportPan]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    let scene: ThreeSimulationScene | null = null;
    const handleControlsStart = () => onViewportPanRef.current();
    const saveCurrentCamera = () => {
      if (scene) saveThreeCamera(props.cameraMemory, scene.getCameraState());
    };
    setSceneError(false);
    try {
      scene = createThreeSimulationScene(host, props.result, props.cameraMemory.three);
      sceneRef.current = scene;
      scene.controls.addEventListener('start', handleControlsStart);
      scene.controls.addEventListener('change', saveCurrentCamera);
      setSceneVersion((version) => version + 1);
    } catch (error: unknown) {
      console.error('Three.js simulation scene initialization failed.', error);
      setSceneError(true);
    }
    return () => {
      if (scene) {
        saveCurrentCamera();
        scene.controls.removeEventListener('start', handleControlsStart);
        scene.controls.removeEventListener('change', saveCurrentCamera);
        destroyThreeSimulationScene(scene);
      }
      if (sceneRef.current === scene) sceneRef.current = null;
    };
  }, [
    props.result.drawing,
    props.result.hazardZones,
    props.result.simulationId,
    props.result.totalPeople,
    sceneRetry,
  ]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const observer = new ResizeObserver(([entry]) => {
      if (!sceneRef.current) return;
      resizeThreeSimulationScene(
        sceneRef.current,
        entry.contentRect.width,
        entry.contentRect.height,
      );
    });
    observer.observe(host);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!sceneRef.current) return;
    updateThreeSimulationScene(
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

  return (
    <div
      ref={hostRef}
      className="simulation-canvas-wrap simulation-three-stage"
      role="application"
      aria-label="시뮬레이션 재생 공간, 3D 보기"
    >
      {!sceneError && (
        <div className="simulation-three-toolbar">
          <span>좌클릭 회전 / 우클릭 이동 / 휠 확대</span>
          <button
            type="button"
            onClick={() => {
              sceneRef.current?.resetCamera();
              if (sceneRef.current) {
                saveThreeCamera(props.cameraMemory, sceneRef.current.getCameraState());
              }
            }}
          >
            시점 초기화
          </button>
        </div>
      )}
      {sceneError && (
        <div className="simulation-canvas-error" role="alert" aria-live="assertive">
          <strong>3D 공간을 불러오지 못했습니다.</strong>
          <span>WebGL을 사용할 수 있는지 확인한 뒤 다시 시도해 주세요.</span>
          <button type="button" onClick={() => setSceneRetry((retry) => retry + 1)}>
            다시 시도
          </button>
        </div>
      )}
    </div>
  );
}
