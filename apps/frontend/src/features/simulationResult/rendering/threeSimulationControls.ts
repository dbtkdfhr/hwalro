import { MOUSE } from 'three';
import type { OrbitControls } from 'three/addons/controls/OrbitControls.js';

export function configureThreeSimulationControls(controls: OrbitControls, span: number) {
  controls.enableDamping = false;
  controls.screenSpacePanning = false;
  controls.panSpeed = 0.75;
  controls.zoomToCursor = true;
  controls.mouseButtons = {
    LEFT: MOUSE.ROTATE,
    MIDDLE: MOUSE.DOLLY,
    RIGHT: MOUSE.PAN,
  };
  controls.minDistance = span * 0.22;
  controls.maxDistance = span * 2.8;
  controls.maxPolarAngle = Math.PI * 0.47;
}
