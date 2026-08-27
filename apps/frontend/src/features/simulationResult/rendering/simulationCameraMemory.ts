export interface PlanCameraState {
  zoom: number;
  panX: number;
  panY: number;
}

export interface ThreeCameraState {
  position: [number, number, number];
  target: [number, number, number];
}

export interface SimulationCameraMemory {
  plan: PlanCameraState;
  three: ThreeCameraState | null;
}

export function createSimulationCameraMemory(): SimulationCameraMemory {
  return {
    plan: { zoom: 1, panX: 0, panY: 0 },
    three: null,
  };
}

export function savePlanCamera(memory: SimulationCameraMemory, camera: PlanCameraState) {
  memory.plan = { ...camera };
}

export function saveThreeCamera(memory: SimulationCameraMemory, camera: ThreeCameraState) {
  memory.three = {
    position: [...camera.position],
    target: [...camera.target],
  };
}
