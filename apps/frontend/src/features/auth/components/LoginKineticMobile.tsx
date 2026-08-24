import { useEffect, useRef } from 'react';

type IdleSchedulerWindow = Window & {
  requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number;
  cancelIdleCallback?: (handle: number) => void;
};

const ROUTE_POINT_COUNT = 34;

const STATIC_ROUTE_POINTS = [
  [18, 76],
  [28, 76],
  [34, 66],
  [42, 60],
  [56, 60],
  [65, 63],
  [76, 62],
  [78, 48],
  [80, 35],
  [84, 28],
] as const;

/**
 * Login-only pathfinding sculpture. Pointer movement changes only this decorative
 * scene and never enters the authentication or simulation data flow.
 */
function LoginKineticMobile() {
  const rootRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const root = rootRef.current;
    const stage = stageRef.current;

    if (!root || !stage || typeof window.matchMedia !== 'function') {
      return;
    }

    const webglSceneQuery = window.matchMedia('(min-width: 1024px)');
    const reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const forcedColorsQuery = window.matchMedia('(forced-colors: active)');

    if (!webglSceneQuery.matches || reducedMotionQuery.matches || forcedColorsQuery.matches) {
      return;
    }

    const idleWindow = window as IdleSchedulerWindow;
    let cancelled = false;
    let idleHandle: number | undefined;
    let timerHandle: number | undefined;
    let disposeScene: (() => void) | undefined;

    const initialiseScene = async () => {
      let rendererForFailureCleanup: import('three').WebGLRenderer | undefined;

      try {
        const THREE = await import('three');

        if (
          cancelled ||
          !root.isConnected ||
          !stage.isConnected ||
          !webglSceneQuery.matches ||
          reducedMotionQuery.matches ||
          forcedColorsQuery.matches
        ) {
          return;
        }

        const scene = new THREE.Scene();
        const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 60);
        camera.position.set(6.7, 7.1, 9.2);
        camera.lookAt(0, 0, 0);

        const renderer = new THREE.WebGLRenderer({
          alpha: true,
          antialias: true,
          powerPreference: 'low-power',
        });
        rendererForFailureCleanup = renderer;
        renderer.setClearColor(0x000000, 0);
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
        renderer.outputColorSpace = THREE.SRGBColorSpace;
        renderer.toneMapping = THREE.ACESFilmicToneMapping;
        renderer.toneMappingExposure = 1.06;
        renderer.domElement.className = 'login-kinetic-mobile__canvas';
        renderer.domElement.setAttribute('aria-hidden', 'true');
        stage.appendChild(renderer.domElement);

        const hemisphereLight = new THREE.HemisphereLight(0xf9fbfa, 0x82938e, 2.35);
        const keyLight = new THREE.DirectionalLight(0xffffff, 3.2);
        const routeLight = new THREE.DirectionalLight(0xc5e84a, 0.48);
        keyLight.position.set(-4, 7, 6);
        routeLight.position.set(5, 3, -2);
        scene.add(hemisphereLight, keyLight, routeLight);

        const floorMaterial = new THREE.MeshStandardMaterial({
          color: 0xf4f7f5,
          metalness: 0.02,
          roughness: 0.88,
        });
        const wallMaterial = new THREE.MeshStandardMaterial({
          color: 0x173b34,
          metalness: 0.03,
          roughness: 0.65,
        });
        const routeMaterial = new THREE.MeshStandardMaterial({
          color: 0xc5e84a,
          emissive: 0x40570a,
          emissiveIntensity: 0.25,
          metalness: 0.02,
          roughness: 0.42,
        });
        const markerMaterial = new THREE.MeshStandardMaterial({
          color: 0x2f675d,
          metalness: 0.04,
          roughness: 0.5,
        });
        const routeLineMaterial = new THREE.LineBasicMaterial({
          color: 0x88a925,
          transparent: true,
          opacity: 0.72,
        });

        const map = new THREE.Group();
        map.position.set(0.25, 0.12, 0);
        map.scale.setScalar(0.94);
        scene.add(map);

        const floor = new THREE.Mesh(new THREE.BoxGeometry(8.6, 0.16, 5.6), floorMaterial);
        floor.position.y = -0.17;
        floor.receiveShadow = true;
        map.add(floor);

        const grid = new THREE.GridHelper(8.2, 18, 0xb8c5c1, 0xd6dfdc);
        grid.position.y = -0.075;
        grid.scale.z = 0.67;
        const gridMaterials = Array.isArray(grid.material) ? grid.material : [grid.material];
        gridMaterials.forEach((material) => {
          material.transparent = true;
          material.opacity = 0.43;
          material.depthWrite = false;
        });
        map.add(grid);

        const addWall = (
          x: number,
          z: number,
          length: number,
          orientation: 'horizontal' | 'vertical',
        ) => {
          const wall = new THREE.Mesh(
            new THREE.BoxGeometry(
              orientation === 'horizontal' ? length : 0.13,
              0.52,
              orientation === 'vertical' ? length : 0.13,
            ),
            wallMaterial,
          );
          wall.position.set(x, 0.17, z);
          map.add(wall);
        };

        addWall(0, -2.58, 8.15, 'horizontal');
        addWall(0, 2.58, 8.15, 'horizontal');
        addWall(-4.08, 0, 5.15, 'vertical');
        addWall(4.08, -0.65, 3.78, 'vertical');
        addWall(4.08, 2.35, 0.45, 'vertical');
        addWall(-2.75, -1.05, 1.55, 'horizontal');
        addWall(-1.55, 0.55, 2.15, 'vertical');
        addWall(-0.25, 0.92, 2.45, 'horizontal');
        addWall(1.12, -1.15, 2.05, 'vertical');
        addWall(2.45, 0.82, 1.6, 'horizontal');
        addWall(2.92, -1.22, 1.1, 'horizontal');

        const exit = new THREE.Group();
        exit.position.set(4.07, 0.2, 1.65);
        const exitPostGeometry = new THREE.BoxGeometry(0.11, 0.7, 0.11);
        const exitBeamGeometry = new THREE.BoxGeometry(0.11, 0.11, 0.82);
        const exitPostA = new THREE.Mesh(exitPostGeometry, routeMaterial);
        const exitPostB = new THREE.Mesh(exitPostGeometry, routeMaterial);
        const exitBeam = new THREE.Mesh(exitBeamGeometry, routeMaterial);
        exitPostA.position.set(0, 0.15, -0.35);
        exitPostB.position.set(0, 0.15, 0.35);
        exitBeam.position.set(0, 0.5, 0);
        exit.add(exitPostA, exitPostB, exitBeam);
        map.add(exit);

        const baseRoutePoints = [
          new THREE.Vector3(-3.42, 0.06, -1.92),
          new THREE.Vector3(-1.82, 0.06, -1.92),
          new THREE.Vector3(-1.78, 0.06, -0.72),
          new THREE.Vector3(-0.45, 0.06, -0.72),
          new THREE.Vector3(0.42, 0.06, 0.08),
          new THREE.Vector3(1.55, 0.06, 0.1),
          new THREE.Vector3(3.48, 0.06, 0.16),
          new THREE.Vector3(3.72, 0.06, 1.65),
        ];
        const routePoints = baseRoutePoints.map((point) => point.clone());
        const routeCurve = new THREE.CatmullRomCurve3(routePoints, false, 'catmullrom', 0.16);
        const routePositions = new Float32Array(ROUTE_POINT_COUNT * 3);
        const routeGeometry = new THREE.BufferGeometry();
        routeGeometry.setAttribute('position', new THREE.BufferAttribute(routePositions, 3));
        const routeLine = new THREE.Line(routeGeometry, routeLineMaterial);
        routeLine.position.y = 0.012;
        map.add(routeLine);

        const routeDotGeometry = new THREE.SphereGeometry(0.075, 12, 8);
        const routeDots = new THREE.InstancedMesh(
          routeDotGeometry,
          routeMaterial,
          ROUTE_POINT_COUNT,
        );
        routeDots.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
        map.add(routeDots);

        const startRing = new THREE.Mesh(
          new THREE.TorusGeometry(0.27, 0.045, 12, 30),
          markerMaterial,
        );
        startRing.rotation.x = Math.PI / 2;
        startRing.position.copy(baseRoutePoints[0]);
        startRing.position.y = 0.085;
        map.add(startRing);

        const routePulse = new THREE.Mesh(new THREE.SphereGeometry(0.16, 18, 12), markerMaterial);
        map.add(routePulse);

        const routeSample = new THREE.Vector3();
        const dotTransform = new THREE.Object3D();
        const updateRoute = (progress: number) => {
          const safeProgress = Number.isFinite(progress)
            ? THREE.MathUtils.clamp(progress, 0, 1)
            : 0;
          for (let index = 0; index < ROUTE_POINT_COUNT; index += 1) {
            const position = index / (ROUTE_POINT_COUNT - 1);
            routeCurve.getPoint(position, routeSample);
            routePositions[index * 3] = routeSample.x;
            routePositions[index * 3 + 1] = routeSample.y;
            routePositions[index * 3 + 2] = routeSample.z;

            const wave = 0.82 + 0.16 * Math.sin(safeProgress * Math.PI * 2 - position * 8);
            dotTransform.position.copy(routeSample);
            dotTransform.scale.setScalar(wave);
            dotTransform.updateMatrix();
            routeDots.setMatrixAt(index, dotTransform.matrix);
          }
          routeGeometry.attributes.position.needsUpdate = true;
          routeDots.instanceMatrix.needsUpdate = true;
          routeCurve.getPoint(safeProgress, routePulse.position);
          routePulse.position.y += 0.09;
        };

        let targetPointerX = 0;
        let targetPointerY = 0;
        let pointerX = 0;
        let pointerY = 0;

        const handlePointerMove = (event: PointerEvent) => {
          const bounds = stage.getBoundingClientRect();
          if (
            !bounds.width ||
            !bounds.height ||
            !Number.isFinite(event.clientX) ||
            !Number.isFinite(event.clientY)
          ) {
            return;
          }
          targetPointerX = ((event.clientX - bounds.left) / bounds.width) * 2 - 1;
          targetPointerY = ((event.clientY - bounds.top) / bounds.height) * 2 - 1;
        };

        const handlePointerLeave = () => {
          targetPointerX = 0;
          targetPointerY = 0;
        };

        stage.addEventListener('pointermove', handlePointerMove, { passive: true });
        stage.addEventListener('pointerleave', handlePointerLeave);

        const resize = () => {
          const width = Math.max(stage.clientWidth, 1);
          const height = Math.max(stage.clientHeight, 1);
          camera.aspect = width / height;
          camera.fov = camera.aspect < 1.08 ? 38 : 34;
          camera.updateProjectionMatrix();
          renderer.setSize(width, height, false);
          renderer.render(scene, camera);
        };

        let resizeObserver: ResizeObserver | undefined;
        if (typeof ResizeObserver === 'function') {
          resizeObserver = new ResizeObserver(resize);
          resizeObserver.observe(stage);
        } else {
          window.addEventListener('resize', resize);
        }
        resize();

        let animationFrame = 0;
        let elapsed = 0;
        let previousTime = performance.now();

        const stopAnimation = () => {
          if (animationFrame) {
            window.cancelAnimationFrame(animationFrame);
            animationFrame = 0;
          }
        };

        const renderFrame = (time: number) => {
          if (
            document.hidden ||
            !webglSceneQuery.matches ||
            reducedMotionQuery.matches ||
            forcedColorsQuery.matches
          ) {
            animationFrame = 0;
            return;
          }

          const frameTime = Number.isFinite(time) ? time : performance.now();
          elapsed += Math.min(Math.max((frameTime - previousTime) / 1000, 0), 0.05);
          previousTime = frameTime;
          pointerX += (targetPointerX - pointerX) * 0.055;
          pointerY += (targetPointerY - pointerY) * 0.055;

          camera.position.x += (6.7 + pointerX * 0.72 - camera.position.x) * 0.045;
          camera.position.y += (7.1 - pointerY * 0.45 - camera.position.y) * 0.045;
          camera.position.z += (9.2 + pointerY * 0.32 - camera.position.z) * 0.045;
          camera.lookAt(0, 0, 0);

          routePoints[3].z = baseRoutePoints[3].z + pointerY * 0.2;
          routePoints[4].x = baseRoutePoints[4].x + pointerX * 0.24;
          routePoints[5].z = baseRoutePoints[5].z - pointerY * 0.18;
          updateRoute((elapsed * 0.075) % 1);

          routePulse.scale.setScalar(0.92 + Math.sin(elapsed * 3.2) * 0.08);
          exit.rotation.y = Math.sin(elapsed * 0.8) * 0.025;
          renderer.render(scene, camera);
          animationFrame = window.requestAnimationFrame(renderFrame);
        };

        const startAnimation = () => {
          if (
            animationFrame ||
            document.hidden ||
            !webglSceneQuery.matches ||
            reducedMotionQuery.matches ||
            forcedColorsQuery.matches
          ) {
            return;
          }
          previousTime = performance.now();
          animationFrame = window.requestAnimationFrame(renderFrame);
        };

        const syncRenderMode = () => {
          if (!webglSceneQuery.matches || reducedMotionQuery.matches || forcedColorsQuery.matches) {
            stopAnimation();
            root.classList.remove('is-webgl-ready');
          } else {
            root.classList.add('is-webgl-ready');
            startAnimation();
          }
        };

        const handleVisibilityChange = () => {
          if (document.hidden) {
            stopAnimation();
          } else {
            startAnimation();
          }
        };

        document.addEventListener('visibilitychange', handleVisibilityChange);
        webglSceneQuery.addEventListener('change', syncRenderMode);
        reducedMotionQuery.addEventListener('change', syncRenderMode);
        forcedColorsQuery.addEventListener('change', syncRenderMode);
        updateRoute(0);
        routeDots.computeBoundingSphere();
        root.classList.add('is-webgl-ready');
        startAnimation();

        disposeScene = () => {
          stopAnimation();
          document.removeEventListener('visibilitychange', handleVisibilityChange);
          webglSceneQuery.removeEventListener('change', syncRenderMode);
          reducedMotionQuery.removeEventListener('change', syncRenderMode);
          forcedColorsQuery.removeEventListener('change', syncRenderMode);
          stage.removeEventListener('pointermove', handlePointerMove);
          stage.removeEventListener('pointerleave', handlePointerLeave);
          resizeObserver?.disconnect();
          window.removeEventListener('resize', resize);
          root.classList.remove('is-webgl-ready');

          const geometries = new Set<import('three').BufferGeometry>();
          const materials = new Set<import('three').Material>();
          scene.traverse((object) => {
            if (object instanceof THREE.Mesh || object instanceof THREE.Line) {
              geometries.add(object.geometry);
              const objectMaterials = Array.isArray(object.material)
                ? object.material
                : [object.material];
              objectMaterials.forEach((material) => materials.add(material));
            }
          });
          geometries.forEach((geometry) => geometry.dispose());
          materials.forEach((material) => material.dispose());
          grid.geometry.dispose();
          gridMaterials.forEach((material) => material.dispose());
          renderer.dispose();
          renderer.forceContextLoss();
          renderer.domElement.remove();
        };
        rendererForFailureCleanup = undefined;
      } catch {
        rendererForFailureCleanup?.dispose();
        rendererForFailureCleanup?.forceContextLoss();
        rendererForFailureCleanup?.domElement.remove();
        root.classList.remove('is-webgl-ready');
      }
    };

    if (idleWindow.requestIdleCallback) {
      idleHandle = idleWindow.requestIdleCallback(() => void initialiseScene(), { timeout: 1200 });
    } else {
      timerHandle = window.setTimeout(() => void initialiseScene(), 350);
    }

    return () => {
      cancelled = true;
      if (idleHandle !== undefined) {
        idleWindow.cancelIdleCallback?.(idleHandle);
      }
      if (timerHandle !== undefined) {
        window.clearTimeout(timerHandle);
      }
      disposeScene?.();
    };
  }, []);

  return (
    <div ref={rootRef} className="login-kinetic-mobile" aria-hidden="true">
      <div className="login-kinetic-mobile__halo" />
      <div className="login-kinetic-mobile__fallback">
        <div className="login-kinetic-mobile__map">
          <span className="login-kinetic-mobile__wall login-kinetic-mobile__wall--top" />
          <span className="login-kinetic-mobile__wall login-kinetic-mobile__wall--left" />
          <span className="login-kinetic-mobile__wall login-kinetic-mobile__wall--middle" />
          <span className="login-kinetic-mobile__wall login-kinetic-mobile__wall--right" />
          <span className="login-kinetic-mobile__wall login-kinetic-mobile__wall--lower" />
          <span className="login-kinetic-mobile__start" />
          <span className="login-kinetic-mobile__exit" />
          <span className="login-kinetic-mobile__route">
            {STATIC_ROUTE_POINTS.map(([left, top]) => (
              <span key={`${left}-${top}`} style={{ left: `${left}%`, top: `${top}%` }} />
            ))}
          </span>
        </div>
      </div>
      <div ref={stageRef} className="login-kinetic-mobile__stage" />
    </div>
  );
}

export default LoginKineticMobile;
