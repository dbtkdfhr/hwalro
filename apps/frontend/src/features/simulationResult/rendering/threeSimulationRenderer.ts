import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import type {
  Bounds,
  DetectedBottleneck,
  DrawingRect,
  RiskZone,
  SimulationResultViewModel,
} from '../types';
import { interpolatePositions, isAgentPositionActive, selectFramePair } from '../utils/playback';
import {
  createBoundarySegments,
  drawingRectContainsPoint,
  escalatorPlacementFromRect,
  escalatorRunElevation,
  exitPortalPlacement,
  isEscalatorLabel,
  projectExitsToBoundarySegments,
  segmentTransform,
  splitBoundarySegmentsAtActiveExits,
  worldToScene,
} from '../../../rendering/three/floorPlanGeometry';
import { boundsTransform } from './threeSimulationGeometry';
import { configureThreeSimulationControls } from './threeSimulationControls';
import { getExitPresentation, type ExitPresentation } from './exitPresentation';
import {
  HAZARD_GRADIENT_FRAGMENT_SHADER,
  HAZARD_GRADIENT_VERTEX_SHADER,
} from './hazardPresentation';
import type { ThreeCameraState } from './simulationCameraMemory';

export interface ThreeSimulationScene {
  renderer: THREE.WebGLRenderer;
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  controls: OrbitControls;
  agentMesh: THREE.InstancedMesh;
  overlayGroup: THREE.Group;
  interpolationBuffer: Float32Array;
  lastOverlayKey: string | null;
  render: () => void;
  getCameraState: () => ThreeCameraState;
  resetCamera: () => void;
}

const WALL_HEIGHT = 2.8;
const WALL_THICKNESS = 0.24;
const AGENT_HEIGHT = 0.72;
const AGENT_RADIUS = 0.18;
const EXIT_FRAME_HEIGHT = 2.5;
const EXIT_BEAM_HEIGHT = 0.26;
const EXIT_FRAME_DEPTH = 0.46;

function addBoxForSegment(
  group: THREE.Group,
  segment: Parameters<typeof segmentTransform>[0],
  worldWidth: number,
  worldHeight: number,
  height: number,
  thickness: number,
  material: THREE.Material,
) {
  const transform = segmentTransform(segment, worldWidth, worldHeight);
  const mesh = new THREE.Mesh(
    new THREE.BoxGeometry(Math.max(0.05, transform.length), height, thickness),
    material,
  );
  mesh.position.set(transform.x, height / 2, transform.z);
  mesh.rotation.y = transform.rotationY;
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  group.add(mesh);
}

function addExitPortal(
  group: THREE.Group,
  exit: Parameters<typeof exitPortalPlacement>[0],
  worldWidth: number,
  worldHeight: number,
  material: THREE.Material,
  presentation: ExitPresentation,
) {
  const placement = exitPortalPlacement(exit, worldWidth, worldHeight);
  const portal = new THREE.Group();
  portal.position.set(placement.x, 0, placement.z);
  portal.rotation.y = placement.rotationY;

  for (const x of [-placement.postOffset, placement.postOffset]) {
    const post = new THREE.Mesh(
      new THREE.BoxGeometry(placement.postWidth, EXIT_FRAME_HEIGHT, EXIT_FRAME_DEPTH),
      material,
    );
    post.position.set(x, EXIT_FRAME_HEIGHT / 2, 0);
    post.castShadow = true;
    post.receiveShadow = true;
    portal.add(post);
  }

  const beam = new THREE.Mesh(
    new THREE.BoxGeometry(placement.width, EXIT_BEAM_HEIGHT, EXIT_FRAME_DEPTH),
    material,
  );
  beam.position.set(0, EXIT_FRAME_HEIGHT - EXIT_BEAM_HEIGHT / 2, 0);
  beam.castShadow = true;
  beam.receiveShadow = true;
  portal.add(beam);

  const openingMarker = new THREE.Mesh(
    new THREE.PlaneGeometry(placement.openingWidth, 0.9),
    new THREE.MeshBasicMaterial({
      color: presentation.color,
      transparent: true,
      opacity: presentation.markerOpacity,
      depthWrite: false,
      side: THREE.DoubleSide,
    }),
  );
  openingMarker.rotation.x = -Math.PI / 2;
  openingMarker.position.y = 0.055;
  portal.add(openingMarker);

  group.add(portal);
}

function addRectStructure(
  group: THREE.Group,
  rectangle: DrawingRect,
  worldWidth: number,
  worldHeight: number,
  structureHeight: number,
  material: THREE.Material,
) {
  const width = Math.max(0.2, Math.abs(rectangle.endX - rectangle.startX));
  const depth = Math.max(0.2, Math.abs(rectangle.endY - rectangle.startY));
  const center = worldToScene(
    (rectangle.startX + rectangle.endX) / 2,
    (rectangle.startY + rectangle.endY) / 2,
    worldWidth,
    worldHeight,
  );
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(width, structureHeight, depth), material);
  mesh.position.set(center.x, structureHeight / 2, center.z);
  mesh.rotation.y = -THREE.MathUtils.degToRad(rectangle.rotation ?? 0);
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  group.add(mesh);
}

function createEscalator(rectangle: DrawingRect, worldWidth: number, worldHeight: number) {
  const group = new THREE.Group();
  const placement = escalatorPlacementFromRect(rectangle, worldWidth, worldHeight);
  const fullLength = Math.max(2.4, placement.length);
  const totalWidth = Math.max(1.4, placement.width);
  const landingLength = Math.min(0.75, Math.max(0.32, fullLength * 0.08));
  const runLength = Math.max(1.4, fullLength - landingLength * 2);
  const runGap = Math.min(0.32, totalWidth * 0.08);
  const runWidth = Math.max(0.5, (totalWidth - runGap) / 2 - 0.12);
  const rise = Math.min(3, Math.max(1.2, runLength * 0.18));
  const stepCount = Math.min(32, Math.max(10, Math.round(runLength / 0.38)));
  const slope = Math.atan2(rise, runLength);
  const railLength = Math.hypot(runLength, rise);
  const stepMaterial = new THREE.MeshStandardMaterial({
    color: 0x899b97,
    roughness: 0.58,
    metalness: 0.24,
  });
  const sideMaterial = new THREE.MeshStandardMaterial({
    color: 0x354b46,
    roughness: 0.45,
    metalness: 0.32,
  });
  const glassMaterial = new THREE.MeshStandardMaterial({
    color: 0x8eb6ad,
    transparent: true,
    opacity: 0.58,
    roughness: 0.2,
    metalness: 0.12,
  });
  const landingMaterial = new THREE.MeshStandardMaterial({
    color: 0x0b8f7f,
    roughness: 0.52,
    metalness: 0.08,
  });
  const runOffset = runWidth / 2 + runGap / 2;

  const runs: Array<{ zOffset: number; direction: 1 | -1 }> = [
    { zOffset: -runOffset, direction: 1 },
    { zOffset: runOffset, direction: -1 },
  ];
  for (const { zOffset, direction } of runs) {
    const side = new THREE.Mesh(
      new THREE.BoxGeometry(railLength, 0.16, runWidth + 0.18),
      sideMaterial,
    );
    side.position.set(0, rise / 2 + 0.09, zOffset);
    side.rotation.z = slope * direction;
    side.castShadow = true;
    group.add(side);

    for (let index = 0; index < stepCount; index += 1) {
      const progress = (index + 0.5) / stepCount;
      const step = new THREE.Mesh(
        new THREE.BoxGeometry((runLength / stepCount) * 0.9, 0.1, runWidth * 0.84),
        stepMaterial,
      );
      step.position.set(
        -runLength / 2 + runLength * progress,
        0.18 + escalatorRunElevation(progress, direction, rise),
        zOffset,
      );
      step.castShadow = true;
      step.receiveShadow = true;
      group.add(step);
    }

    for (const railSide of [-1, 1]) {
      const railZ = zOffset + railSide * (runWidth / 2 + 0.06);
      const rail = new THREE.Mesh(new THREE.BoxGeometry(railLength, 0.09, 0.08), glassMaterial);
      rail.position.set(0, rise / 2 + 0.82, railZ);
      rail.rotation.z = slope * direction;
      group.add(rail);

      for (const endX of [-runLength / 2, runLength / 2]) {
        const highEnd = endX * direction > 0;
        const post = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.72, 0.08), sideMaterial);
        post.position.set(endX, (highEnd ? rise : 0) + 0.48, railZ);
        group.add(post);
      }
    }

    for (const endX of [-fullLength / 2 + landingLength / 2, fullLength / 2 - landingLength / 2]) {
      const highEnd = endX * direction > 0;
      const landing = new THREE.Mesh(
        new THREE.BoxGeometry(landingLength, 0.12, runWidth),
        landingMaterial,
      );
      landing.position.set(endX, (highEnd ? rise : 0) + 0.08, zOffset);
      group.add(landing);
    }
  }

  group.position.set(placement.x, 0, placement.z);
  group.rotation.y = placement.rotationY;
  return group;
}

function createFloor(result: SimulationResultViewModel) {
  const { drawing } = result;
  const group = new THREE.Group();
  const backdrop = new THREE.Mesh(
    new THREE.PlaneGeometry(drawing.width + 8, drawing.height + 8),
    new THREE.MeshStandardMaterial({ color: 0xdfe6e3, roughness: 0.96, metalness: 0 }),
  );
  backdrop.rotation.x = -Math.PI / 2;
  backdrop.position.y = -0.05;
  backdrop.receiveShadow = true;
  group.add(backdrop);

  if (drawing.outsideBoundary.length >= 3) {
    const shape = new THREE.Shape();
    drawing.outsideBoundary.forEach((point, index) => {
      const scenePoint = worldToScene(point.x, point.y, drawing.width, drawing.height);
      if (index === 0) shape.moveTo(scenePoint.x, scenePoint.z);
      else shape.lineTo(scenePoint.x, scenePoint.z);
    });
    shape.closePath();
    const floor = new THREE.Mesh(
      new THREE.ShapeGeometry(shape),
      new THREE.MeshStandardMaterial({
        color: 0xfafcfb,
        roughness: 0.88,
        metalness: 0,
        side: THREE.DoubleSide,
      }),
    );
    floor.rotation.x = Math.PI / 2;
    floor.receiveShadow = true;
    group.add(floor);
  }

  return group;
}

function createStructures(result: SimulationResultViewModel) {
  const { drawing } = result;
  const group = new THREE.Group();
  const outsideWallMaterial = new THREE.MeshStandardMaterial({
    color: 0x667d77,
    roughness: 0.74,
    metalness: 0.02,
  });
  const wallMaterial = new THREE.MeshStandardMaterial({ color: 0xb7c6c2, roughness: 0.78 });
  const pillarMaterial = new THREE.MeshStandardMaterial({ color: 0x8ea39e, roughness: 0.72 });
  const fabricMaterial = new THREE.MeshStandardMaterial({ color: 0xd5dfdc, roughness: 0.86 });
  const exitMaterials = new Map<boolean, THREE.MeshStandardMaterial>();
  for (const active of [false, true]) {
    const presentation = getExitPresentation(active);
    exitMaterials.set(
      active,
      new THREE.MeshStandardMaterial({
        color: presentation.color,
        emissive: presentation.emissive,
        emissiveIntensity: presentation.emissiveIntensity,
        roughness: 0.6,
        metalness: 0.02,
      }),
    );
  }

  const boundarySegments = createBoundarySegments(drawing.outsideBoundary);
  const boundaryExits = projectExitsToBoundarySegments(boundarySegments, drawing.exits);
  const outsideWalls = splitBoundarySegmentsAtActiveExits(boundarySegments, boundaryExits);
  for (const wall of outsideWalls) {
    addBoxForSegment(group, wall, drawing.width, drawing.height, 3.2, 0.34, outsideWallMaterial);
  }
  for (const wall of drawing.walls) {
    addBoxForSegment(
      group,
      wall,
      drawing.width,
      drawing.height,
      WALL_HEIGHT,
      WALL_THICKNESS,
      wallMaterial,
    );
  }
  const escalatorLabels = drawing.layoutTexts.filter((label) => isEscalatorLabel(label.text));
  const structures = [...drawing.pillars, ...drawing.fabrics];
  const replacedStructures = new Set<DrawingRect>();
  const escalatorStructures = escalatorLabels.map((label) => {
    const matchingStructure = structures
      .filter(
        (structure) =>
          !replacedStructures.has(structure) &&
          drawingRectContainsPoint(structure, { x: label.x, y: label.y }),
      )
      .sort(
        (first, second) =>
          Math.abs(first.endX - first.startX) * Math.abs(first.endY - first.startY) -
          Math.abs(second.endX - second.startX) * Math.abs(second.endY - second.startY),
      )[0];
    if (matchingStructure) replacedStructures.add(matchingStructure);
    return (
      matchingStructure ?? {
        name: `escalator-${label.text}`,
        startX: label.x - 1.2,
        startY: label.y - 3,
        endX: label.x + 1.2,
        endY: label.y + 3,
        rotation: 0,
      }
    );
  });
  for (const pillar of drawing.pillars.filter((pillar) => !replacedStructures.has(pillar))) {
    addRectStructure(group, pillar, drawing.width, drawing.height, 2.5, pillarMaterial);
  }
  for (const fabric of drawing.fabrics.filter((fabric) => !replacedStructures.has(fabric))) {
    addRectStructure(group, fabric, drawing.width, drawing.height, 1.55, fabricMaterial);
  }
  for (const exit of boundaryExits) {
    const presentation = getExitPresentation(exit.active);
    const material = exitMaterials.get(exit.active);
    if (!material) continue;
    addExitPortal(group, exit, drawing.width, drawing.height, material, presentation);
  }
  for (const structure of escalatorStructures) {
    group.add(createEscalator(structure, drawing.width, drawing.height));
  }
  return group;
}

function createHazards(result: SimulationResultViewModel) {
  const group = new THREE.Group();
  if (result.hazardZones.length === 0) return group;
  const fillMaterial = new THREE.ShaderMaterial({
    vertexShader: HAZARD_GRADIENT_VERTEX_SHADER,
    fragmentShader: HAZARD_GRADIENT_FRAGMENT_SHADER,
    transparent: true,
    depthWrite: false,
    side: THREE.DoubleSide,
    toneMapped: false,
  });
  for (const hazard of result.hazardZones) {
    const center = worldToScene(
      hazard.centerX,
      hazard.centerY,
      result.drawing.width,
      result.drawing.height,
    );
    const fill = new THREE.Mesh(new THREE.CircleGeometry(hazard.radius, 48), fillMaterial);
    fill.rotation.x = -Math.PI / 2;
    fill.position.set(center.x, 0.045, center.z);
    group.add(fill);

    const ring = new THREE.Mesh(
      new THREE.RingGeometry(hazard.radius * 0.96, hazard.radius, 64),
      new THREE.MeshBasicMaterial({
        color: 0xc43630,
        transparent: true,
        opacity: 0.8,
        side: THREE.DoubleSide,
      }),
    );
    ring.rotation.x = -Math.PI / 2;
    ring.position.set(center.x, 0.055, center.z);
    group.add(ring);
  }
  return group;
}

function disposeObject(object: THREE.Object3D) {
  object.traverse((child) => {
    if (!(child instanceof THREE.Mesh) && !(child instanceof THREE.LineSegments)) return;
    child.geometry.dispose();
    const materials = Array.isArray(child.material) ? child.material : [child.material];
    for (const material of materials) material.dispose();
  });
}

export function createThreeSimulationScene(
  host: HTMLDivElement,
  result: SimulationResultViewModel,
  savedCamera: ThreeCameraState | null = null,
): ThreeSimulationScene {
  const renderer = new THREE.WebGLRenderer({
    antialias: true,
    alpha: true,
    powerPreference: 'high-performance',
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(Math.max(1, host.clientWidth), Math.max(1, host.clientHeight), false);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.domElement.className = 'simulation-three-canvas';
  renderer.domElement.setAttribute('aria-hidden', 'true');

  let createdScene: THREE.Scene | null = null;
  let createdControls: OrbitControls | null = null;
  try {
    const scene = new THREE.Scene();
    createdScene = scene;
    scene.background = new THREE.Color(0xdfe6e3);
    scene.fog = new THREE.Fog(
      0xdfe6e3,
      Math.max(result.drawing.width, result.drawing.height) * 1.2,
      Math.max(result.drawing.width, result.drawing.height) * 2.7,
    );

    const camera = new THREE.PerspectiveCamera(
      42,
      Math.max(1, host.clientWidth) / Math.max(1, host.clientHeight),
      0.1,
      Math.max(500, Math.max(result.drawing.width, result.drawing.height) * 8),
    );
    const controls = new OrbitControls(camera, renderer.domElement);
    createdControls = controls;
    const span = Math.max(18, result.drawing.width, result.drawing.height);
    const initialPosition = new THREE.Vector3(span * 0.75, span * 1.05, span * 1.15);
    const initialTarget = new THREE.Vector3(0, 0, 0);
    camera.position.copy(initialPosition);
    controls.target.copy(initialTarget);
    configureThreeSimulationControls(controls, span);
    if (savedCamera) {
      camera.position.fromArray(savedCamera.position);
      controls.target.fromArray(savedCamera.target);
    }

    scene.add(new THREE.HemisphereLight(0xffffff, 0xa6b9b3, 2.25));
    const keyLight = new THREE.DirectionalLight(0xffffff, 2.2);
    keyLight.position.set(-span * 0.5, span, span * 0.45);
    keyLight.castShadow = true;
    keyLight.shadow.mapSize.set(1024, 1024);
    keyLight.shadow.camera.left = -span;
    keyLight.shadow.camera.right = span;
    keyLight.shadow.camera.top = span;
    keyLight.shadow.camera.bottom = -span;
    scene.add(keyLight);
    scene.add(createFloor(result), createStructures(result), createHazards(result));

    const agentMesh = new THREE.InstancedMesh(
      new THREE.CapsuleGeometry(AGENT_RADIUS, AGENT_HEIGHT - AGENT_RADIUS * 2, 3, 8),
      new THREE.MeshStandardMaterial({ color: 0x0b8f7f, roughness: 0.62, metalness: 0.02 }),
      Math.max(1, result.totalPeople),
    );
    agentMesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    agentMesh.frustumCulled = false;
    scene.add(agentMesh);

    const overlayGroup = new THREE.Group();
    scene.add(overlayGroup);
    const render = () => renderer.render(scene, camera);
    controls.addEventListener('change', render);
    controls.update();
    host.appendChild(renderer.domElement);
    render();

    return {
      renderer,
      scene,
      camera,
      controls,
      agentMesh,
      overlayGroup,
      interpolationBuffer: new Float32Array(result.totalPeople * 2),
      lastOverlayKey: null,
      render,
      getCameraState: () => ({
        position: camera.position.toArray(),
        target: controls.target.toArray(),
      }),
      resetCamera: () => {
        camera.position.copy(initialPosition);
        controls.target.copy(initialTarget);
        controls.update();
        render();
      },
    };
  } catch (error: unknown) {
    createdControls?.dispose();
    if (createdScene) disposeObject(createdScene);
    renderer.dispose();
    renderer.forceContextLoss();
    renderer.domElement.remove();
    throw error;
  }
}

function addOverlayBox(
  group: THREE.Group,
  bounds: Bounds,
  worldWidth: number,
  worldHeight: number,
  color: number,
  opacity: number,
  height = 0.08,
) {
  const transform = boundsTransform(bounds, worldWidth, worldHeight);
  const mesh = new THREE.Mesh(
    new THREE.BoxGeometry(transform.width, height, transform.depth),
    new THREE.MeshBasicMaterial({ color, transparent: true, opacity, depthWrite: false }),
  );
  mesh.position.set(transform.x, height / 2 + 0.08, transform.z);
  group.add(mesh);
  const edges = new THREE.LineSegments(
    new THREE.EdgesGeometry(mesh.geometry),
    new THREE.LineBasicMaterial({ color, transparent: true, opacity: Math.min(1, opacity * 3) }),
  );
  edges.position.copy(mesh.position);
  group.add(edges);
}

function updateOverlays(
  target: ThreeSimulationScene,
  result: SimulationResultViewModel,
  bottlenecks: DetectedBottleneck[],
  selectedBottleneckId: number | null,
  showBottlenecks: boolean,
  riskZones: RiskZone[],
  currentTimeSeconds: number,
) {
  const overlayKey = JSON.stringify({
    showBottlenecks,
    selectedBottleneckId,
    bottlenecks: showBottlenecks
      ? bottlenecks.map((item) => [
          item.id,
          currentTimeSeconds >= item.startTimeSeconds && currentTimeSeconds <= item.endTimeSeconds,
          item.geometry,
        ])
      : [],
    riskZones,
  });
  if (target.lastOverlayKey === overlayKey) return;
  target.lastOverlayKey = overlayKey;
  for (const child of [...target.overlayGroup.children]) {
    target.overlayGroup.remove(child);
    disposeObject(child);
  }
  if (showBottlenecks) {
    for (const bottleneck of bottlenecks) {
      const active =
        currentTimeSeconds >= bottleneck.startTimeSeconds &&
        currentTimeSeconds <= bottleneck.endTimeSeconds;
      addOverlayBox(
        target.overlayGroup,
        bottleneck.geometry,
        result.drawing.width,
        result.drawing.height,
        bottleneck.id === selectedBottleneckId ? 0xd93f35 : 0xeb6847,
        active ? 0.28 : 0.12,
      );
    }
  }
  for (const zone of riskZones) {
    addOverlayBox(
      target.overlayGroup,
      zone,
      result.drawing.width,
      result.drawing.height,
      0x5c75d9,
      0.16,
    );
  }
}

export function updateThreeSimulationScene(
  target: ThreeSimulationScene,
  result: SimulationResultViewModel,
  bottlenecks: DetectedBottleneck[],
  currentTimeSeconds: number,
  selectedBottleneckId: number | null,
  showBottlenecks: boolean,
  riskZones: RiskZone[],
) {
  const pair = selectFramePair(result.agentFrames, currentTimeSeconds);
  interpolatePositions(
    pair.previous.positions,
    pair.next.positions,
    pair.ratio,
    target.interpolationBuffer,
  );
  const dummy = new THREE.Object3D();
  for (let index = 0; index < target.agentMesh.count; index += 1) {
    const offset = index * 2;
    if (!isAgentPositionActive(target.interpolationBuffer, offset)) {
      dummy.position.set(0, -100, 0);
      dummy.scale.setScalar(0.001);
    } else {
      const point = worldToScene(
        target.interpolationBuffer[offset],
        target.interpolationBuffer[offset + 1],
        result.drawing.width,
        result.drawing.height,
      );
      dummy.position.set(point.x, AGENT_HEIGHT / 2 + 0.06, point.z);
      dummy.scale.setScalar(1);
    }
    dummy.updateMatrix();
    target.agentMesh.setMatrixAt(index, dummy.matrix);
  }
  target.agentMesh.instanceMatrix.needsUpdate = true;
  updateOverlays(
    target,
    result,
    bottlenecks,
    selectedBottleneckId,
    showBottlenecks,
    riskZones,
    currentTimeSeconds,
  );
  target.render();
}

export function resizeThreeSimulationScene(
  target: ThreeSimulationScene,
  width: number,
  height: number,
) {
  target.camera.aspect = Math.max(1, width) / Math.max(1, height);
  target.camera.updateProjectionMatrix();
  target.renderer.setSize(Math.max(1, width), Math.max(1, height), false);
  target.render();
}

export function destroyThreeSimulationScene(target: ThreeSimulationScene) {
  target.controls.removeEventListener('change', target.render);
  target.controls.dispose();
  disposeObject(target.scene);
  target.renderer.dispose();
  target.renderer.forceContextLoss();
  target.renderer.domElement.remove();
}
