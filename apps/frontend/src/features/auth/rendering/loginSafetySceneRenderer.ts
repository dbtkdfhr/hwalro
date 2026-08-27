import * as THREE from 'three';

interface LoginSafetySceneController {
  destroy: () => void;
}

export interface WallSegment {
  startX: number;
  startZ: number;
  endX: number;
  endZ: number;
  height?: number;
}

interface MovingAgent {
  mesh: THREE.Mesh;
  curve: THREE.Curve<THREE.Vector3>;
  offset: number;
  speed: number;
}

const FLOOR_WIDTH = 17;
const FLOOR_DEPTH = 11;
export const LOGIN_OUTSIDE_WALL_HEIGHT = 1.82;
export const LOGIN_INSIDE_WALL_HEIGHT = 1.48;
const WALL_DEPTH = 0.28;
const EXIT_COLOR = 0xabcf30;
const MIN_CAMERA_VIEW_HEIGHT = 14.2;
const MAX_CAMERA_VIEW_HEIGHT = 15.2;
const CAMERA_WIDE_ASPECT_START = 4 / 3;
const CAMERA_BASE_ROTATION = -0.05;
const CAMERA_POINTER_ROTATION_RANGE = 0.18;

export function calculateLoginSceneViewHeight(aspect: number) {
  const wideScreenAllowance = Math.max(0, aspect - CAMERA_WIDE_ASPECT_START) * 1.15;
  return THREE.MathUtils.clamp(
    MIN_CAMERA_VIEW_HEIGHT + wideScreenAllowance,
    MIN_CAMERA_VIEW_HEIGHT,
    MAX_CAMERA_VIEW_HEIGHT,
  );
}

export function calculateLoginSceneTargetRotation(pointerRatio: number) {
  return CAMERA_BASE_ROTATION + pointerRatio * CAMERA_POINTER_ROTATION_RANGE;
}

export const OUTSIDE_WALLS: WallSegment[] = [
  { startX: -8.5, startZ: -5.5, endX: 8.5, endZ: -5.5 },
  { startX: -8.5, startZ: 5.5, endX: -8.5, endZ: -5.5 },
  { startX: 8.5, startZ: -5.5, endX: 8.5, endZ: 5.5 },
  { startX: -8.5, startZ: 5.5, endX: 4.8, endZ: 5.5 },
  { startX: 6.8, startZ: 5.5, endX: 8.5, endZ: 5.5 },
];

export const INSIDE_WALLS: WallSegment[] = [
  {
    startX: -5.8,
    startZ: -3.7,
    endX: -1.6,
    endZ: -3.7,
    height: LOGIN_INSIDE_WALL_HEIGHT,
  },
  {
    startX: -1.6,
    startZ: -3.7,
    endX: -1.6,
    endZ: -0.8,
    height: LOGIN_INSIDE_WALL_HEIGHT,
  },
  {
    startX: -6.2,
    startZ: 0.2,
    endX: -2.6,
    endZ: 0.2,
    height: LOGIN_INSIDE_WALL_HEIGHT,
  },
  {
    startX: -2.6,
    startZ: 0.2,
    endX: -2.6,
    endZ: 3.2,
    height: LOGIN_INSIDE_WALL_HEIGHT,
  },
  {
    startX: 0.6,
    startZ: -5.5,
    endX: 0.6,
    endZ: -1.5,
    height: LOGIN_INSIDE_WALL_HEIGHT,
  },
  {
    startX: 0.6,
    startZ: 1.2,
    endX: 0.6,
    endZ: 4.1,
    height: LOGIN_INSIDE_WALL_HEIGHT,
  },
  {
    startX: 3.2,
    startZ: -2.1,
    endX: 6.5,
    endZ: -2.1,
    height: LOGIN_INSIDE_WALL_HEIGHT,
  },
  {
    startX: 3.2,
    startZ: 1.3,
    endX: 7.2,
    endZ: 1.3,
    height: LOGIN_INSIDE_WALL_HEIGHT,
  },
];

function addWall(parent: THREE.Group, segment: WallSegment, material: THREE.MeshStandardMaterial) {
  const deltaX = segment.endX - segment.startX;
  const deltaZ = segment.endZ - segment.startZ;
  const length = Math.hypot(deltaX, deltaZ);
  const height = segment.height ?? LOGIN_OUTSIDE_WALL_HEIGHT;
  const wall = new THREE.Mesh(new THREE.BoxGeometry(length, height, WALL_DEPTH), material);
  wall.position.set(
    (segment.startX + segment.endX) / 2,
    height / 2,
    (segment.startZ + segment.endZ) / 2,
  );
  wall.rotation.y = -Math.atan2(deltaZ, deltaX);
  wall.castShadow = true;
  wall.receiveShadow = true;
  parent.add(wall);
}

function addExitPortal(parent: THREE.Group) {
  const material = new THREE.MeshStandardMaterial({
    color: EXIT_COLOR,
    emissive: 0x526b10,
    emissiveIntensity: 0.22,
    roughness: 0.58,
  });
  const portal = new THREE.Group();
  portal.position.set(5.8, 0, 5.5);

  for (const x of [-0.88, 0.88]) {
    const post = new THREE.Mesh(new THREE.BoxGeometry(0.24, 2.25, 0.42), material);
    post.position.set(x, 1.125, 0);
    post.castShadow = true;
    portal.add(post);
  }

  const beam = new THREE.Mesh(new THREE.BoxGeometry(2, 0.24, 0.42), material);
  beam.position.set(0, 2.13, 0);
  beam.castShadow = true;
  portal.add(beam);

  const opening = new THREE.Mesh(
    new THREE.PlaneGeometry(1.52, 1.15),
    new THREE.MeshBasicMaterial({
      color: EXIT_COLOR,
      transparent: true,
      opacity: 0.2,
      depthWrite: false,
      side: THREE.DoubleSide,
    }),
  );
  opening.rotation.x = -Math.PI / 2;
  opening.position.set(0, 0.025, -0.18);
  portal.add(opening);
  parent.add(portal);
}

export const LOGIN_SCENE_ROUTE_POINTS: Array<Array<[number, number]>> = [
  [
    [-7.1, -4.2],
    [-6.45, -4.2],
    [-6.45, -0.3],
    [-1.05, -0.3],
    [-1.05, 0.7],
    [2.2, 0.7],
    [2.2, 3.6],
    [5.8, 3.6],
    [5.8, 5.85],
  ],
  [
    [7.2, -3.8],
    [2.1, -3.8],
    [2.1, -0.8],
    [1.3, -0.8],
    [1.3, 0.7],
    [2.2, 0.7],
    [2.2, 3.6],
    [5.8, 3.6],
    [5.8, 5.85],
  ],
];

export function createRoute(points: Array<[number, number]>) {
  const route = new THREE.CurvePath<THREE.Vector3>();
  const vectors = points.map(([x, z]) => new THREE.Vector3(x, 0.11, z));
  for (let index = 1; index < vectors.length; index += 1) {
    route.add(new THREE.LineCurve3(vectors[index - 1], vectors[index]));
  }
  return route;
}

function disposeObject(root: THREE.Object3D) {
  root.traverse((child) => {
    if (!(child instanceof THREE.Mesh) && !(child instanceof THREE.Line)) return;
    child.geometry.dispose();
    const materials = Array.isArray(child.material) ? child.material : [child.material];
    materials.forEach((material) => material.dispose());
  });
}

export function createLoginSafetyScene(
  host: HTMLDivElement,
  reducedMotion: boolean,
): LoginSafetySceneController {
  const renderer = new THREE.WebGLRenderer({
    antialias: true,
    alpha: true,
    powerPreference: 'high-performance',
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.7));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFShadowMap;
  renderer.setClearColor(0xf2f6f4, 0);

  let createdScene: THREE.Scene | null = null;
  let createdTimer: THREE.Timer | null = null;
  let createdResizeObserver: ResizeObserver | null = null;
  let pointerMoveHandler: ((event: PointerEvent) => void) | null = null;
  let pointerLeaveHandler: (() => void) | null = null;
  let animationFrame = 0;

  try {
    const scene = new THREE.Scene();
    createdScene = scene;
    const camera = new THREE.OrthographicCamera(-10, 10, 7, -7, 0.1, 100);
    camera.position.set(13.5, 14.5, 17.5);
    const cameraTarget = new THREE.Vector3(0, 0, 0.4);
    camera.lookAt(cameraTarget);
    const framingOffset = new THREE.Vector3(0.52, 0.72, 0).applyQuaternion(camera.quaternion);
    camera.position.add(framingOffset);
    cameraTarget.add(framingOffset);
    camera.lookAt(cameraTarget);

    const model = new THREE.Group();
    model.rotation.y = CAMERA_BASE_ROTATION;
    scene.add(model);

    const floor = new THREE.Mesh(
      new THREE.BoxGeometry(FLOOR_WIDTH, 0.18, FLOOR_DEPTH),
      new THREE.MeshStandardMaterial({ color: 0xf9fbfa, roughness: 0.92 }),
    );
    floor.position.y = -0.1;
    floor.receiveShadow = true;
    model.add(floor);

    const grid = new THREE.GridHelper(FLOOR_WIDTH, 17, 0xcbd8d4, 0xe4ebe8);
    grid.scale.z = FLOOR_DEPTH / FLOOR_WIDTH;
    grid.position.y = 0.006;
    const gridMaterials = Array.isArray(grid.material) ? grid.material : [grid.material];
    gridMaterials.forEach((material) => {
      material.transparent = true;
      material.opacity = 0.62;
    });
    model.add(grid);

    const outsideMaterial = new THREE.MeshStandardMaterial({
      color: 0x173a33,
      roughness: 0.7,
    });
    const insideMaterial = new THREE.MeshStandardMaterial({
      color: 0x315c53,
      roughness: 0.74,
    });
    OUTSIDE_WALLS.forEach((wall) => addWall(model, wall, outsideMaterial));
    INSIDE_WALLS.forEach((wall) => addWall(model, wall, insideMaterial));
    addExitPortal(model);

    const routes = LOGIN_SCENE_ROUTE_POINTS.map(createRoute);

    const routeMaterial = new THREE.MeshBasicMaterial({
      color: EXIT_COLOR,
      transparent: true,
      opacity: 0.42,
    });
    routes.forEach((route) => {
      const path = new THREE.Mesh(
        new THREE.TubeGeometry(route, 84, 0.026, 6, false),
        routeMaterial,
      );
      model.add(path);
    });

    const agents: MovingAgent[] = [];
    const agentGeometry = new THREE.SphereGeometry(0.14, 14, 10);
    const agentMaterial = new THREE.MeshStandardMaterial({ color: 0xb6d843, roughness: 0.6 });
    const leadMaterial = new THREE.MeshStandardMaterial({ color: 0x0f766e, roughness: 0.56 });
    for (let index = 0; index < 22; index += 1) {
      const curve = routes[index % routes.length];
      const mesh = new THREE.Mesh(agentGeometry, index === 0 ? leadMaterial : agentMaterial);
      mesh.castShadow = true;
      model.add(mesh);
      agents.push({
        mesh,
        curve,
        offset: (index * 0.087) % 1,
        speed: index % 2 === 0 ? 0.026 : 0.023,
      });
    }

    scene.add(new THREE.HemisphereLight(0xffffff, 0xa2b5af, 2.5));
    const keyLight = new THREE.DirectionalLight(0xffffff, 3.1);
    keyLight.position.set(-7, 13, 9);
    keyLight.castShadow = true;
    keyLight.shadow.mapSize.set(1024, 1024);
    keyLight.shadow.camera.left = -14;
    keyLight.shadow.camera.right = 14;
    keyLight.shadow.camera.top = 14;
    keyLight.shadow.camera.bottom = -14;
    scene.add(keyLight);

    let targetRotation = model.rotation.y;
    const timer = new THREE.Timer();
    createdTimer = timer;
    timer.connect(document);

    const placeAgents = (elapsed: number) => {
      agents.forEach((agent, index) => {
        const progress = (agent.offset + elapsed * agent.speed) % 1;
        const point = agent.curve.getPointAt(progress);
        agent.mesh.position.set(point.x, index === 0 ? 0.29 : 0.2, point.z);
        const scale = index === 0 ? 1.65 : 1;
        agent.mesh.scale.setScalar(scale);
      });
    };

    const render = () => renderer.render(scene, camera);
    const resize = () => {
      const width = Math.max(1, host.clientWidth);
      const height = Math.max(1, host.clientHeight);
      const aspect = width / height;
      const viewHeight = calculateLoginSceneViewHeight(aspect);
      camera.left = (-viewHeight * aspect) / 2;
      camera.right = (viewHeight * aspect) / 2;
      camera.top = viewHeight / 2;
      camera.bottom = -viewHeight / 2;
      camera.updateProjectionMatrix();
      renderer.setSize(width, height, false);
      render();
    };

    const handlePointerMove = (event: PointerEvent) => {
      const bounds = host.getBoundingClientRect();
      const pointerX = (event.clientX - bounds.left) / Math.max(1, bounds.width) - 0.5;
      targetRotation = calculateLoginSceneTargetRotation(pointerX);
    };
    const handlePointerLeave = () => {
      targetRotation = CAMERA_BASE_ROTATION;
    };
    pointerMoveHandler = handlePointerMove;
    pointerLeaveHandler = handlePointerLeave;

    const animate = () => {
      animationFrame = window.requestAnimationFrame(animate);
      if (document.hidden) return;
      timer.update();
      placeAgents(timer.getElapsed());
      model.rotation.y += (targetRotation - model.rotation.y) * 0.045;
      render();
    };

    const resizeObserver = new ResizeObserver(resize);
    createdResizeObserver = resizeObserver;
    resizeObserver.observe(host);
    if (!reducedMotion) {
      host.addEventListener('pointermove', handlePointerMove);
      host.addEventListener('pointerleave', handlePointerLeave);
      placeAgents(0);
      animationFrame = window.requestAnimationFrame(animate);
    } else {
      placeAgents(0);
    }
    resize();
    host.appendChild(renderer.domElement);

    return {
      destroy: () => {
        window.cancelAnimationFrame(animationFrame);
        resizeObserver.disconnect();
        host.removeEventListener('pointermove', handlePointerMove);
        host.removeEventListener('pointerleave', handlePointerLeave);
        timer.dispose();
        disposeObject(scene);
        renderer.dispose();
        renderer.forceContextLoss();
        renderer.domElement.remove();
      },
    };
  } catch (error: unknown) {
    window.cancelAnimationFrame(animationFrame);
    createdResizeObserver?.disconnect();
    if (pointerMoveHandler) host.removeEventListener('pointermove', pointerMoveHandler);
    if (pointerLeaveHandler) host.removeEventListener('pointerleave', pointerLeaveHandler);
    createdTimer?.dispose();
    if (createdScene) disposeObject(createdScene);
    renderer.dispose();
    renderer.forceContextLoss();
    renderer.domElement.remove();
    throw error;
  }
}
