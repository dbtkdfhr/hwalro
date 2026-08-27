import { describe, expect, it } from 'vitest';
import {
  createSimulationCameraMemory,
  savePlanCamera,
  saveThreeCamera,
} from './simulationCameraMemory';

describe('simulation camera memory', () => {
  it('keeps the latest 2D and 3D viewpoints independently across scene recreation', () => {
    const memory = createSimulationCameraMemory();

    savePlanCamera(memory, { zoom: 1.8, panX: 120, panY: -45 });
    saveThreeCamera(memory, {
      position: [18, 24, 31],
      target: [3, 0, -2],
    });

    expect(memory.plan).toEqual({ zoom: 1.8, panX: 120, panY: -45 });
    expect(memory.three).toEqual({
      position: [18, 24, 31],
      target: [3, 0, -2],
    });
  });

  it('copies 3D vector tuples so renderer-owned values cannot mutate the saved viewpoint', () => {
    const memory = createSimulationCameraMemory();
    const position: [number, number, number] = [10, 20, 30];
    const target: [number, number, number] = [1, 2, 3];

    saveThreeCamera(memory, { position, target });
    position[0] = 999;
    target[0] = 999;

    expect(memory.three).toEqual({
      position: [10, 20, 30],
      target: [1, 2, 3],
    });
  });
});
