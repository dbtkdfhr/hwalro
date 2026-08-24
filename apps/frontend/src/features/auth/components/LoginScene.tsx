import { useEffect, useRef } from 'react';
import * as THREE from 'three';

type Point = readonly [number, number];

const SKY = '#eef6f2';
const LIME = '#c2e84b';
const EMERALD = '#168f80';
const ACCENT = '#0e6f63';

const WALL_HEIGHT = 1.3;
const WALL_THICKNESS = 0.35;

/** 건물 도면 벽 세그먼트 [x1, z1, x2, z2] — 출구는 남쪽(x -7~-4)과 동쪽(z -1.5~1.5) */
const WALL_SEGMENTS: readonly [number, number, number, number][] = [
  [-12, -8, 12, -8],
  [-12, -8, -12, 8],
  [12, -8, 12, -1.5],
  [12, 1.5, 12, 8],
  [-12, 8, -7, 8],
  [-4, 8, 12, 8],
  [-4, -8, -4, -5],
  [-4, -3.5, -4, -1],
  [4, -8, 4, -4.5],
  [4, -3, 4, 0],
  [4, 2, 8, 2],
  [9, 2, 12, 2],
];

/** 에이전트 이동 경로 — 각 경로의 마지막 지점은 출구 밖이다 */
const ROUTES: readonly Point[][] = [
  [
    [-8, -6],
    [-4.5, -4.25],
    [-1.5, -4.25],
    [-1, -1.5],
    [-3, 0.5],
    [-9, 1],
    [-10, 4],
    [-9, 6.8],
    [-5.5, 7.2],
    [-5.5, 10.5],
  ],
  [
    [0, -6.5],
    [0.5, -2],
    [-1, 0.5],
    [-8, 1.2],
    [-10, 4],
    [-9, 6.8],
    [-5.5, 7.2],
    [-5.5, 10.5],
  ],
  [
    [6.5, -6],
    [9, -5],
    [9.5, -2],
    [13.8, 0],
  ],
  [
    [0.5, -3.75],
    [2, -1.5],
    [4.5, 0.8],
    [8, 1],
    [12.5, 0.6],
    [13.8, 0],
  ],
  [
    [-6, 3],
    [-9.5, 4],
    [-9, 6.8],
    [-5.5, 7.2],
    [-5.5, 10.5],
  ],
  [
    [1, 1],
    [-2, 2.5],
    [-7, 3],
    [-9.5, 4.5],
    [-9, 6.8],
    [-5.5, 7.2],
    [-5.5, 10.5],
  ],
  [
    [7, 4.5],
    [8.5, 2.8],
    [8.5, 1],
    [11, 0.5],
    [13.8, 0],
  ],
];

const AGENT_COUNTS_PER_ROUTE = [30, 26, 22, 22, 20, 20, 18];

interface RoutePath {
  points: Point[];
  cumulative: number[];
  total: number;
}

function buildRoute(points: Point[]): RoutePath {
  const cumulative = [0];
  for (let i = 1; i < points.length; i += 1) {
    const dx = points[i][0] - points[i - 1][0];
    const dz = points[i][1] - points[i - 1][1];
    cumulative.push(cumulative[i - 1] + Math.hypot(dx, dz));
  }
  return { points, cumulative, total: cumulative[cumulative.length - 1] };
}

function sampleRoute(route: RoutePath, distance: number): { x: number; z: number } {
  const { points, cumulative } = route;
  const s = Math.min(Math.max(distance, 0), route.total);
  let i = 1;
  while (i < cumulative.length - 1 && cumulative[i] < s) i += 1;
  const segLength = cumulative[i] - cumulative[i - 1] || 1;
  const t = (s - cumulative[i - 1]) / segLength;
  return {
    x: points[i - 1][0] + (points[i][0] - points[i - 1][0]) * t,
    z: points[i - 1][1] + (points[i][1] - points[i - 1][1]) * t,
  };
}

interface Agent {
  route: RoutePath;
  distance: number;
  speed: number;
  laneOffset: number;
  swayPhase: number;
  swayFreq: number;
  scale: number;
}

function createAgents(): Agent[] {
  const agents: Agent[] = [];
  ROUTES.forEach((points, routeIndex) => {
    const route = buildRoute(points);
    for (let i = 0; i < AGENT_COUNTS_PER_ROUTE[routeIndex]; i += 1) {
      agents.push({
        route,
        distance: Math.random() * route.total,
        speed: 1.6 * (0.7 + Math.random() * 0.6),
        laneOffset: (Math.random() - 0.5) * 1.1,
        swayPhase: Math.random() * Math.PI * 2,
        swayFreq: 0.8 + Math.random() * 0.7,
        scale: 0.85 + Math.random() * 0.35,
      });
    }
  });
  return agents;
}

function LoginScene() {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;

    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(SKY);
    scene.fog = new THREE.Fog(SKY, 40, 90);

    const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 200);
    const renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.domElement.style.width = '100%';
    renderer.domElement.style.height = '100%';
    renderer.domElement.style.display = 'block';
    container.appendChild(renderer.domElement);

    const hemiLight = new THREE.HemisphereLight('#ffffff', '#d7e9e2', 1.15);
    scene.add(hemiLight);
    const dirLight = new THREE.DirectionalLight('#ffffff', 1.5);
    dirLight.position.set(10, 18, 6);
    scene.add(dirLight);

    const grid = new THREE.GridHelper(70, 70, '#c4dcd4', '#aecbc3');
    const gridMaterial = grid.material as THREE.Material;
    gridMaterial.transparent = true;
    gridMaterial.opacity = 0.7;
    scene.add(grid);

    const interiorFloor = new THREE.Mesh(
      new THREE.BoxGeometry(25.5, 0.24, 17.5),
      new THREE.MeshStandardMaterial({ color: '#ffffff', roughness: 1 }),
    );
    interiorFloor.position.set(0, -0.12, 0);
    scene.add(interiorFloor);

    const wallGeometryByLength = new Map<number, THREE.BoxGeometry>();
    const wallMaterial = new THREE.MeshStandardMaterial({ color: '#dfefea', roughness: 0.85 });
    const edgeMaterial = new THREE.LineBasicMaterial({
      color: '#168f80',
      transparent: true,
      opacity: 0.5,
    });

    for (const [x1, z1, x2, z2] of WALL_SEGMENTS) {
      const length = Math.hypot(x2 - x1, z2 - z1);
      let geometry = wallGeometryByLength.get(length);
      if (!geometry) {
        geometry = new THREE.BoxGeometry(WALL_THICKNESS, WALL_HEIGHT, length);
        wallGeometryByLength.set(length, geometry);
      }
      const wall = new THREE.Mesh(geometry, wallMaterial);
      wall.position.set((x1 + x2) / 2, WALL_HEIGHT / 2, (z1 + z2) / 2);
      wall.rotation.y = Math.abs(x2 - x1) > Math.abs(z2 - z1) ? Math.PI / 2 : 0;
      scene.add(wall);

      const edges = new THREE.LineSegments(new THREE.EdgesGeometry(geometry), edgeMaterial);
      edges.position.copy(wall.position);
      edges.rotation.copy(wall.rotation);
      scene.add(edges);
    }

    const exitMaterials: THREE.MeshBasicMaterial[] = [];
    const exitGlowSpecs: readonly { position: Point; width: number; depth: number }[] = [
      { position: [-5.5, 9.2], width: 3, depth: 1.2 },
      { position: [13.2, 0], width: 1.2, depth: 3 },
    ];
    for (const spec of exitGlowSpecs) {
      const material = new THREE.MeshBasicMaterial({
        color: ACCENT,
        transparent: true,
        opacity: 0.3,
      });
      exitMaterials.push(material);
      const glow = new THREE.Mesh(new THREE.PlaneGeometry(spec.width, spec.depth), material);
      glow.rotation.x = -Math.PI / 2;
      glow.position.set(spec.position[0], 0.03, spec.position[1]);
      scene.add(glow);
    }

    const agents = createAgents();
    const agentGeometry = new THREE.CapsuleGeometry(0.16, 0.3, 3, 8);
    const agentMaterial = new THREE.MeshBasicMaterial({ toneMapped: false });
    const agentMesh = new THREE.InstancedMesh(agentGeometry, agentMaterial, agents.length);
    agentMesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);

    const palette = [
      new THREE.Color(LIME),
      new THREE.Color(LIME),
      new THREE.Color(EMERALD),
      new THREE.Color(ACCENT),
    ];
    for (let i = 0; i < agents.length; i += 1) {
      agentMesh.setColorAt(i, palette[Math.floor(Math.random() * palette.length)]);
    }
    if (agentMesh.instanceColor) agentMesh.instanceColor.needsUpdate = true;
    scene.add(agentMesh);

    const dummy = new THREE.Object3D();
    function updateAgents(elapsed: number, delta: number) {
      for (let i = 0; i < agents.length; i += 1) {
        const agent = agents[i];
        agent.distance += agent.speed * delta;
        if (agent.distance > agent.route.total + 6) {
          agent.distance = -Math.random() * 8;
          agent.laneOffset = (Math.random() - 0.5) * 1.1;
        }
        if (agent.distance < 0) {
          dummy.position.set(0, -10, 0);
          dummy.scale.setScalar(0.001);
          dummy.updateMatrix();
          agentMesh.setMatrixAt(i, dummy.matrix);
          continue;
        }

        const { x, z } = sampleRoute(agent.route, agent.distance);
        const ahead = sampleRoute(agent.route, agent.distance + 0.4);
        const dirX = ahead.x - x;
        const dirZ = ahead.z - z;
        const dirLength = Math.hypot(dirX, dirZ) || 1;
        const normalX = -dirZ / dirLength;
        const normalZ = dirX / dirLength;
        const sway = agent.laneOffset + Math.sin(elapsed * agent.swayFreq + agent.swayPhase) * 0.18;

        const growIn = Math.min(agent.distance / 1.2, 1);
        const remaining = agent.route.total - agent.distance;
        const shrinkOut = Math.min(Math.max(remaining / 1.2, 0), 1);

        dummy.position.set(x + normalX * sway, 0.42 * agent.scale, z + normalZ * sway);
        dummy.scale.setScalar(agent.scale * growIn * shrinkOut);
        dummy.updateMatrix();
        agentMesh.setMatrixAt(i, dummy.matrix);
      }
      agentMesh.instanceMatrix.needsUpdate = true;
    }

    const pointerTarget = { x: 0, y: 0 };
    const pointerCurrent = { x: 0, y: 0 };
    function handlePointerMove(event: PointerEvent) {
      pointerTarget.x = (event.clientX / window.innerWidth) * 2 - 1;
      pointerTarget.y = (event.clientY / window.innerHeight) * 2 - 1;
    }
    window.addEventListener('pointermove', handlePointerMove);

    const CAMERA_RADIUS = 42;
    const BASE_ELEVATION = 0.88;
    const BASE_AZIMUTH = -0.55;
    function updateCamera(elapsed: number) {
      pointerCurrent.x += (pointerTarget.x - pointerCurrent.x) * 0.04;
      pointerCurrent.y += (pointerTarget.y - pointerCurrent.y) * 0.04;
      const azimuth = BASE_AZIMUTH + Math.sin(elapsed * 0.05) * 0.07 + pointerCurrent.x * 0.06;
      const elevation = BASE_ELEVATION + pointerCurrent.y * 0.04;
      camera.position.set(
        Math.sin(azimuth) * Math.cos(elevation) * CAMERA_RADIUS,
        Math.sin(elevation) * CAMERA_RADIUS,
        Math.cos(azimuth) * Math.cos(elevation) * CAMERA_RADIUS,
      );
      camera.lookAt(1.5, 0, 0.5);
    }

    const resize = () => {
      const width = container.clientWidth;
      const height = container.clientHeight;
      if (width === 0 || height === 0) return;
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
      renderer.setSize(width, height, false);
    };
    resize();
    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(container);

    updateAgents(0, 0);
    updateCamera(0);
    renderer.render(scene, camera);

    let frameId = 0;
    const clock = new THREE.Clock();
    function animate() {
      const delta = Math.min(clock.getDelta(), 0.05);
      const elapsed = clock.elapsedTime;
      updateAgents(elapsed, delta);
      for (let i = 0; i < exitMaterials.length; i += 1) {
        exitMaterials[i].opacity = 0.3 + Math.sin(elapsed * 1.6 + i * 2.1) * 0.12;
      }
      updateCamera(elapsed);
      renderer.render(scene, camera);
      frameId = requestAnimationFrame(animate);
    }
    if (!prefersReducedMotion) {
      frameId = requestAnimationFrame(animate);
    }

    return () => {
      cancelAnimationFrame(frameId);
      window.removeEventListener('pointermove', handlePointerMove);
      resizeObserver.disconnect();
      wallGeometryByLength.forEach((geometry) => geometry.dispose());
      scene.traverse((object) => {
        if (object instanceof THREE.Mesh || object instanceof THREE.LineSegments) {
          object.geometry.dispose();
          const material = object.material;
          if (Array.isArray(material)) {
            material.forEach((item) => item.dispose());
          } else {
            material.dispose();
          }
        }
      });
      if (renderer.domElement.parentElement === container) {
        container.removeChild(renderer.domElement);
      }
      renderer.dispose();
    };
  }, []);

  return <div ref={containerRef} aria-hidden className="pointer-events-none absolute inset-0" />;
}

export default LoginScene;
